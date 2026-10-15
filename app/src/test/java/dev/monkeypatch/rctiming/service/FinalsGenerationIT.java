package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest.ClassFinalsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Generating rounds, seeding finals and bumping drivers up, against a real database with its
 * foreign keys and unique constraints (#45). The unit tests mock the repositories, so they never
 * caught the placeholder rows that pointed at entry 0.
 */
class FinalsGenerationIT extends AbstractIntegrationTest {

    @Autowired RoundGeneratorService roundGeneratorService;
    @Autowired BumpUpSeedingService bumpUpSeedingService;
    @Autowired EventRunOrderService runOrderService;
    @Autowired QualifyingStandingsService qualifyingStandingsService;
    @Autowired AutoFinalsSeedingService autoFinalsSeedingService;
    @Autowired QualifyingTableService qualifyingTableService;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired JdbcTemplate jdbc;

    private String run;
    private long eventId;
    private final List<Long> competitorIds = new ArrayList<>();
    private final List<Long> racingClassIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, date('now'), 'IN_PROGRESS') returning id""",
                Long.class, "Finals event " + run);
    }

    /** The test database is shared, so leave no in-progress event behind for the boards to pick up. */
    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        jdbc.update("delete from rounds where event_id = ?", eventId); // races and race_entries cascade
        jdbc.update("delete from entries where event_id = ?", eventId);
        jdbc.update("delete from event_classes where event_id = ?", eventId);
        jdbc.update("delete from events where id = ?", eventId);
        competitorIds.forEach(id -> jdbc.update("delete from competitors where id = ?", id));
        racingClassIds.forEach(id -> jdbc.update("delete from racing_classes where id = ?", id));
    }

    @Test
    void generatesRoundsSeedsFinalsAndBumpsDriversUp() {
        long buggyClass = addClass("Buggy");
        long truckClass = addClass("Truck");
        List<Long> buggies = addEntries(buggyClass, 20);
        addEntries(truckClass, 6);

        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2),
                new ClassFinalsConfig(truckClass, 1))));

        List<Race> buggyFinals = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.FINAL);
        assertThat(buggyFinals).extracting(Race::getFinalLetter).containsExactlyInAnyOrder("A", "B");
        assertThat(raceRepository.findByEventClassIdAndRoundType(truckClass, RoundType.FINAL)).hasSize(1);
        // Finals start with empty grids
        for (Race f : buggyFinals) {
            assertThat(raceEntryRepository.findByRaceIdOrderByGridPosition(f.getId())).isEmpty();
        }

        // Qualifying order: the buggies in the order they were added
        bumpUpSeedingService.seedFinals(buggyClass, buggies, 2, 10, 2);

        Race aFinal = finalOf(buggyClass, "A");
        Race bFinal = finalOf(buggyClass, "B");
        assertThat(aFinal.getBumpSlots()).isEqualTo(2);
        assertThat(bFinal.getBumpSlots()).isZero();
        assertThat(gridOf(aFinal)).extracting(RaceEntry::getEntryId).containsExactlyElementsOf(buggies.subList(0, 8));
        assertThat(gridOf(bFinal)).extracting(RaceEntry::getEntryId).containsExactlyElementsOf(buggies.subList(10, 20));

        // Seeding again replaces the grids rather than adding to them
        bumpUpSeedingService.seedFinals(buggyClass, buggies, 2, 10, 2);
        assertThat(gridOf(aFinal)).hasSize(8);
        assertThat(gridOf(bFinal)).hasSize(10);

        // The B-final finishes in reverse grid order; its top two go up
        List<Long> bFinishingOrder = new ArrayList<>(buggies.subList(10, 20));
        Collections.reverse(bFinishingOrder);
        List<Long> promoted = bumpUpSeedingService.applyBumpUpResults(bFinal.getId(), bFinishingOrder);

        assertThat(promoted).containsExactly(buggies.get(19), buggies.get(18));
        List<RaceEntry> aGrid = gridOf(aFinal);
        assertThat(aGrid).hasSize(10);
        assertThat(aGrid.subList(8, 10)).allMatch(RaceEntry::isBumped);
        assertThat(aGrid.subList(8, 10)).extracting(RaceEntry::getEntryId)
                .containsExactly(buggies.get(19), buggies.get(18));
        assertThat(aGrid.subList(8, 10)).extracting(RaceEntry::getGridPosition).containsExactly(9, 10);
        assertThat(aGrid.subList(8, 10)).extracting(RaceEntry::getCarNumber).containsExactly(9, 10);

        // A repeat (race finished again after a resume) promotes nobody else
        assertThat(bumpUpSeedingService.applyBumpUpResults(bFinal.getId(), bFinishingOrder)).isEmpty();
        assertThat(gridOf(aFinal)).hasSize(10);

        // The promotion is in the audit log as the system's, once: the repeat promoted nobody
        List<Map<String, Object>> audited = jdbc.queryForList(
                "select * from audit_log where event_id = ? and action = 'BUMP_UP_APPLIED'", eventId);
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).get("actor_user_id")).isNull();
        assertThat(audited.get(0).get("actor_label")).isEqualTo("system:bump-up");
        assertThat(audited.get(0).get("summary").toString()).contains("Moved 2 up").contains("B final");
        assertThat(((Number) audited.get(0).get("race_id")).longValue()).isEqualTo(aFinal.getId());
        jdbc.update("delete from audit_log where event_id = ?", eventId);

        // Every race_entries row for this event points at a real entry
        Integer orphans = jdbc.queryForObject("""
                select count(*) from race_entries re
                join races r on r.id = re.race_id join rounds ro on ro.id = r.round_id
                left join entries e on e.id = re.entry_id
                where ro.event_id = ? and e.id is null""", Integer.class, eventId);
        assertThat(orphans).isZero();
    }

    /** Races take their start from the class's format, and a rolling start can now be stored on a race (#142). */
    @Test
    void racesTakeTheirStartTypeFromTheClassFormat() {
        long racingClassId = jdbc.queryForObject(
                "insert into racing_classes (name) values (?) returning id", Long.class, "Rolling " + run);
        racingClassIds.add(racingClassId);
        long rollingClass = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"BUMP_UP","qualifyingStartType":"GRID","finalsStartType":"ROLLING"}')
                returning id""", Long.class, eventId, racingClassId);
        addEntries(rollingClass, 6);

        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 1, 1, 10, List.of(
                new ClassFinalsConfig(rollingClass, 1))));

        assertThat(raceRepository.findByEventClassIdAndRoundType(rollingClass, RoundType.PRACTICE))
                .extracting(Race::getStartType).containsOnly(StartType.GRID);
        assertThat(raceRepository.findByEventClassIdAndRoundType(rollingClass, RoundType.QUALIFIER))
                .extracting(Race::getStartType).containsOnly(StartType.GRID);
        assertThat(raceRepository.findByEventClassIdAndRoundType(rollingClass, RoundType.FINAL))
                .extracting(Race::getStartType).containsExactly(StartType.ROLLING);
    }

    @Test
    void seedsFinalsFromTheStoredQualifyingResultsOverAllHeatsAndRefusesBeforeThereAreAny() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));

        // Nothing has finished, so there is nothing to seed from and the grids are left alone
        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 2))
                .isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId + 1_000_000, buggyClass,
                2, 10, 2)).isInstanceOf(EntityNotFoundException.class);

        // Everyone does 10 laps in each of the two rounds, except the first entry, who does one more in round 1.
        // Round 1 best laps are all slow; in round 2 the later the entry, the quicker its best lap.
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                    heat.getRoundId());
            finishWithResult(heat, buggies, round);
        }
        runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 2);

        // The first entry has the most laps; the rest tie on laps, so the quickest best lap (the last entry) is next
        List<Long> order = new ArrayList<>();
        order.add(buggies.get(0));
        List<Long> rest = new ArrayList<>(buggies.subList(1, buggies.size()));
        Collections.reverse(rest);
        order.addAll(rest);
        assertThat(gridOf(finalOf(buggyClass, "A"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(0, 8));
        assertThat(gridOf(finalOf(buggyClass, "B"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(10, 20));

        List<Map<String, Object>> audited = jdbc.queryForList(
                "select * from audit_log where event_id = ? and action = 'FINALS_SEEDED'", eventId);
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0).get("summary").toString()).contains("from 20 qualifiers");
    }

    @Test
    void showsTheOverallQualifyingTableAndSeedsTheFinalsFromItOnRequest() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                    heat.getRoundId());
            finishWithResult(heat, buggies, round);
        }

        var table = qualifyingTableService.forEvent(eventId).stream()
                .filter(t -> t.eventClassId() == buggyClass).findFirst().orElseThrow();
        assertThat(table.rows()).hasSize(20);
        assertThat(table.rows().get(0).entryId()).isEqualTo(buggies.get(0));
        assertThat(table.rows().get(0).driverName()).startsWith("Driver " + buggyClass + "-1 ");
        assertThat(table.rows().get(0).lapsCompleted()).isEqualTo(21);
        assertThat(table.finalsStarted()).isFalse();

        autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass);

        // An even split of the table over the two finals: ranks 1-10 in the A final, 11-20 in the B final
        List<Long> order = table.rows().stream().map(r -> r.entryId()).toList();
        assertThat(gridOf(finalOf(buggyClass, "A"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(0, 10));
        assertThat(gridOf(finalOf(buggyClass, "B"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(10, 20));
    }

    @Test
    void reseedingIsRefusedUntilEveryQualifyingHeatIsFinished() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        List<Race> heats = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER);
        Race first = heats.get(0);
        int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                first.getRoundId());
        finishWithResult(first, buggies, round);

        assertThatThrownBy(() -> autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass))
                .isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> autoFinalsSeedingService.reseed(Actor.system("test"), eventId + 1_000_000,
                buggyClass)).isInstanceOf(EntityNotFoundException.class);
    }

    /** Marks the heat finished (as the race control does) and stores its result; each row is entry, laps, best lap. */
    private void finishWithResult(Race heat, List<Long> buggies, int round) {
        List<long[]> rows = new ArrayList<>();
        for (RaceEntry entry : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
            int index = buggies.indexOf(entry.getEntryId());
            int laps = 10 + (index == 0 && round == 1 ? 1 : 0);
            rows.add(new long[] {entry.getEntryId(), laps, round == 1 ? 60_000 : 50_000 - index * 100L});
        }
        storeResult(heat, rows, false);
    }

    private void storeResult(Race heat, List<long[]> rows, boolean abandoned) {
        jdbc.update("update races set status = 'FINISHED' where id = ?", heat.getId());
        if (abandoned) {
            jdbc.update("update races set abandoned_at = CAST(unixepoch('subsec') * 1000000 AS INTEGER)"
                    + " where id = ?", heat.getId());
        }
        List<String> json = new ArrayList<>();
        int position = 1;
        for (long[] row : rows) {
            json.add("{\"position\":" + position++ + ",\"entryId\":" + row[0]
                    + ",\"competitorId\":null,\"driverName\":\"d\",\"carNumber\":\"1\",\"lapsCompleted\":" + row[1]
                    + ",\"totalTimeMs\":300000,\"bestLapMs\":" + row[2] + ",\"gapToLeaderMs\":null}");
        }
        jdbc.update("""
                insert into result_snapshots (race_id, finished_at, positions_json, lap_history_json)
                values (?, CAST(unixepoch('subsec') * 1000000 AS INTEGER), ?, '[]')""",
                heat.getId(), "[" + String.join(",", json) + "]");
    }

    @Test
    void standingsRankEveryoneOnTheGridOfAFinishedHeatAndLeaveOutAbandonedLapsAndWithdrawnEntries() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        List<Race> heats = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER);
        List<Long> first = raceEntryRepository.findByRaceIdOrderByGridPosition(heats.get(0).getId()).stream()
                .map(RaceEntry::getEntryId).toList();
        List<Long> second = raceEntryRepository.findByRaceIdOrderByGridPosition(heats.get(1).getId()).stream()
                .map(RaceEntry::getEntryId).toList();

        // Heat 1 finishes with a result for all but its last driver, who never crossed the line.
        // Heat 2 is abandoned: its laps do not count. One driver of heat 1 has since withdrawn.
        List<long[]> rows = new ArrayList<>();
        for (int i = 0; i < first.size() - 1; i++) {
            rows.add(new long[] {first.get(i), 10 + i, 50_000});
        }
        storeResult(heats.get(0), rows, false);
        List<long[]> abandonedRows = new ArrayList<>();
        second.forEach(id -> abandonedRows.add(new long[] {id, 99, 40_000}));
        storeResult(heats.get(1), abandonedRows, true);
        long withdrawn = first.get(0);
        jdbc.update("update entries set status = 'WITHDRAWN' where id = ?", withdrawn);

        List<Long> expected = new ArrayList<>();
        for (int i = first.size() - 2; i >= 1; i--) {
            expected.add(first.get(i));
        }
        List<Long> noLaps = new ArrayList<>(second);
        noLaps.add(first.get(first.size() - 1));
        Collections.sort(noLaps);
        expected.addAll(noLaps);

        assertThat(qualifyingStandingsService.standingsFor(buggyClass)).containsExactlyElementsOf(expected)
                .doesNotContain(withdrawn).hasSize(19);
        assertThat(buggies).containsAll(expected);
    }

    @Test
    void seedingIsRefusedOnceAFinalHasStartedAndAnOverlargeBumpCountIsRefused() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            finishWithResult(heat, buggies, 1);
        }
        runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 2);
        Race aFinal = finalOf(buggyClass, "A");
        int seeded = gridOf(aFinal).size();

        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 11))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update races set status = 'RUNNING' where id = ?", aFinal.getId());
        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 2))
                .isInstanceOf(StateConflictException.class);

        assertThat(gridOf(aFinal)).hasSize(seeded);
    }

    private long addClass(String name) {
        long racingClassId = jdbc.queryForObject(
                "insert into racing_classes (name) values (?) returning id", Long.class, name + " " + run);
        racingClassIds.add(racingClassId);
        return jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED"}') returning id""", Long.class, eventId, racingClassId);
    }

    private List<Long> addEntries(long eventClassId, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            long competitorId = jdbc.queryForObject(
                    "insert into competitors (display_name) values (?) returning id", Long.class,
                    "Driver " + eventClassId + "-" + i + " " + run);
            competitorIds.add(competitorId);
            ids.add(jdbc.queryForObject("""
                    insert into entries (event_id, event_class_id, competitor_id, transponder_number, status)
                    values (?, ?, ?, ?, 'CONFIRMED') returning id""", Long.class,
                    eventId, eventClassId, competitorId, String.valueOf(eventClassId * 100 + i)));
        }
        return ids;
    }

    private Race finalOf(long eventClassId, String letter) {
        return raceRepository.findByEventClassIdAndFinalLetter(eventClassId, letter).get(0);
    }

    private List<RaceEntry> gridOf(Race race) {
        return raceEntryRepository.findByRaceIdOrderByGridPosition(race.getId()).stream()
                .sorted(Comparator.comparing(RaceEntry::getGridPosition)).toList();
    }
}
