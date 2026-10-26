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
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
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
    @Autowired FinalsLineupService finalsLineupService;
    @Autowired QualifyingTableService qualifyingTableService;
    @Autowired FinalLegsService finalLegsService;
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
        jdbc.update("delete from marshal_penalties where event_id = ?", eventId);
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
        bumpUpSeedingService.seedFinals(buggyClass, buggies, 2, 11, 2);

        Race aFinal = finalOf(buggyClass, "A");
        Race bFinal = finalOf(buggyClass, "B");
        assertThat(aFinal.getBumpSlots()).isEqualTo(2);
        assertThat(bFinal.getBumpSlots()).isZero();
        assertThat(gridOf(aFinal)).extracting(RaceEntry::getEntryId).containsExactlyElementsOf(buggies.subList(0, 9));
        assertThat(gridOf(bFinal)).extracting(RaceEntry::getEntryId).containsExactlyElementsOf(buggies.subList(9, 20));

        // Seeding again replaces the grids rather than adding to them
        bumpUpSeedingService.seedFinals(buggyClass, buggies, 2, 11, 2);
        assertThat(gridOf(aFinal)).hasSize(9);
        assertThat(gridOf(bFinal)).hasSize(11);

        // The B-final finishes in reverse grid order; its top two go up
        List<Long> bFinishingOrder = new ArrayList<>(buggies.subList(9, 20));
        Collections.reverse(bFinishingOrder);
        List<Long> promoted = bumpUpSeedingService.applyBumpUpResults(bFinal.getId(), bFinishingOrder);

        assertThat(promoted).containsExactly(buggies.get(19), buggies.get(18));
        List<RaceEntry> aGrid = gridOf(aFinal);
        assertThat(aGrid).hasSize(11);
        assertThat(aGrid.subList(9, 11)).allMatch(RaceEntry::isBumped);
        assertThat(aGrid.subList(9, 11)).extracting(RaceEntry::getEntryId)
                .containsExactly(buggies.get(19), buggies.get(18));
        assertThat(aGrid.subList(9, 11)).extracting(RaceEntry::getGridPosition).containsExactly(10, 11);
        // The bump-ups are numbered by their place on the A grid, in finishing order, not by qualifying rank
        assertThat(aGrid.subList(9, 11)).extracting(RaceEntry::getCarNumber).containsExactly(10, 11);

        // A repeat (race finished again after a resume) promotes nobody else
        assertThat(bumpUpSeedingService.applyBumpUpResults(bFinal.getId(), bFinishingOrder)).isEmpty();
        assertThat(gridOf(aFinal)).hasSize(11);

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
        runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 11, 2);

        // The first entry has the most laps; the rest tie on laps, so the quickest best lap (the last entry) is next
        List<Long> order = new ArrayList<>();
        order.add(buggies.get(0));
        List<Long> rest = new ArrayList<>(buggies.subList(1, buggies.size()));
        Collections.reverse(rest);
        order.addAll(rest);
        assertThat(gridOf(finalOf(buggyClass, "A"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(0, 9));
        assertThat(gridOf(finalOf(buggyClass, "B"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(order.subList(9, 20));

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

        autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass, null);

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

        assertThatThrownBy(() -> autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass, null))
                .isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> autoFinalsSeedingService.reseed(Actor.system("test"), eventId + 1_000_000,
                buggyClass, null)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void roundByRoundQualifyingRanksByLowPointsOverTheBestRoundsAndBreaksTiesOnLapsThenQuickestLap() {
        long buggyClass = addClass("Buggy", """
                {"type":"BUMP_UP","qualifyingHeats":2,"heatDurationMinutes":5,"bestHeatsCount":1,"gridSize":10,
                 "bumpSpots":2,"qualifyingStartType":"STAGGER","finalsStartType":"GRID",
                 "qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5}""");
        List<Long> drivers = addEntries(buggyClass, 4);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 1))));
        // Round 1 places: a, b, c, d. Round 2 places: b, c, d, a. With the best round counting, a and b both have
        // 1 point; each has 10 laps in the counting round, so the quicker best lap (a's) decides
        int[][] laps = {{10, 9, 8, 7}, {7, 10, 9, 8}};
        long[][] bestLap = {{60_000, 61_000, 62_000, 63_000}, {63_000, 61_500, 62_500, 64_000}};
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                    heat.getRoundId()) - 1;
            List<long[]> rows = new ArrayList<>();
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                int d = drivers.indexOf(onGrid.getEntryId());
                rows.add(new long[] {onGrid.getEntryId(), laps[round][d], bestLap[round][d]});
            }
            storeResult(heat, rows, false);
        }

        var ranked = qualifyingStandingsService.rankedResultsFor(buggyClass);

        assertThat(ranked).extracting(r -> r.entryId())
                .containsExactly(drivers.get(0), drivers.get(1), drivers.get(2), drivers.get(3));
        // a: best round 1 point; b: 1 point; c: round 2 place 2; d: round 2 place 3
        assertThat(ranked).extracting(r -> r.points()).containsExactly(1, 1, 2, 3);
        // a counts round 1 and drops round 2 (place 4); b drops round 1 (place 2) and counts round 2
        assertThat(ranked.get(0).rounds()).containsExactly(
                new QualifyingStandingsService.RoundPlace(1, true), new QualifyingStandingsService.RoundPlace(4, false));
        assertThat(ranked.get(1).rounds()).containsExactly(
                new QualifyingStandingsService.RoundPlace(2, false), new QualifyingStandingsService.RoundPlace(1, true));
    }

    @Test
    void aQualifyingMarshalPenaltyDropsTheDriversBestRoundSoTheNextBestCounts() {
        long buggyClass = addClass("Buggy", """
                {"type":"BUMP_UP","qualifyingHeats":2,"heatDurationMinutes":5,"bestHeatsCount":1,"gridSize":10,
                 "bumpSpots":2,"qualifyingStartType":"STAGGER","finalsStartType":"GRID",
                 "qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5}""");
        List<Long> drivers = addEntries(buggyClass, 4);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 1))));
        // Round 1 places: a, b, c, d. Round 2 places: b, c, d, a
        int[][] laps = {{10, 9, 8, 7}, {7, 10, 9, 8}};
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                    heat.getRoundId()) - 1;
            List<long[]> rows = new ArrayList<>();
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                rows.add(new long[] {onGrid.getEntryId(), laps[round][drivers.indexOf(onGrid.getEntryId())], 60_000});
            }
            storeResult(heat, rows, false);
        }
        jdbc.update("insert into marshal_penalties (entry_id, event_id, applied_by, kind)"
                + " values (?, ?, 1, 'QUALIFYING_SCORE')", drivers.get(0), eventId);
        // A lap-in-the-finals penalty does not touch the qualifying order
        jdbc.update("insert into marshal_penalties (entry_id, event_id, applied_by, kind)"
                + " values (?, ?, 1, 'FINALS_LAP')", drivers.get(1), eventId);

        var ranked = qualifyingStandingsService.rankedResultsFor(buggyClass);

        // a loses round 1 (1 point) and keeps round 2 (4 points), so goes from first to last
        assertThat(ranked).extracting(r -> r.entryId())
                .containsExactly(drivers.get(1), drivers.get(2), drivers.get(3), drivers.get(0));
        assertThat(ranked).extracting(r -> r.points()).containsExactly(1, 2, 3, 4);
        // a's round 1 (place 1) was taken away, so it shows as dropped and round 2 (place 4) counts
        assertThat(ranked.get(3).rounds()).containsExactly(
                new QualifyingStandingsService.RoundPlace(1, false), new QualifyingStandingsService.RoundPlace(4, true));
    }

    private static final String ROUND_BY_ROUND_ALL = """
            {"type":"BUMP_UP","qualifyingHeats":2,"heatDurationMinutes":5,"bestHeatsCount":0,"gridSize":10,
             "bumpSpots":2,"qualifyingStartType":"STAGGER","finalsStartType":"GRID",
             "qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5}""";

    @Test
    void roundByRoundQualifyingFallsBackToLapsWhileARoundIsHalfFinished() {
        long buggyClass = addClass("Buggy", ROUND_BY_ROUND_ALL);
        List<Long> drivers = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        Race first = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER).get(0);
        List<long[]> rows = new ArrayList<>();
        for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(first.getId())) {
            rows.add(new long[] {onGrid.getEntryId(), 10, 60_000});
        }
        storeResult(first, rows, false);

        var ranked = qualifyingStandingsService.rankedResultsFor(buggyClass);

        assertThat(ranked).hasSize(rows.size());
        assertThat(ranked).extracting(r -> r.points()).containsOnlyNulls();
        assertThat(drivers).containsAll(ranked.stream().map(r -> r.entryId()).toList());
    }

    @Test
    void theLiveTableCountsTheHeatOnTrackLeavesItOutForTheBaselineAndListsDriversWhoseHeatIsToCome() {
        long buggyClass = addClass("Buggy", ROUND_BY_ROUND_ALL);
        addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        List<Race> heats = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER);
        Race first = heats.get(0);
        Race second = heats.get(1);
        List<Long> firstGrid = raceEntryRepository.findByRaceIdOrderByGridPosition(first.getId()).stream()
                .map(RaceEntry::getEntryId).toList();
        List<Long> secondGrid = raceEntryRepository.findByRaceIdOrderByGridPosition(second.getId()).stream()
                .map(RaceEntry::getEntryId).toList();
        List<long[]> rows = new ArrayList<>();
        firstGrid.forEach(id -> rows.add(new long[] {id, 10, 60_000}));
        storeResult(first, rows, false);

        // The second heat is on track and its first car is ahead of everyone in the first heat
        List<LiveTimingRowDto> live = new ArrayList<>();
        for (int i = 0; i < secondGrid.size(); i++) {
            int laps = i == 0 ? 12 : 9;
            live.add(new LiveTimingRowDto(secondGrid.get(i), "d", "1", i + 1, laps, 0L, 14_000L, 13_000L, 14_000L,
                    13_000L, 0, 0, null, null, 300_000L + i, false, 0L));
        }

        var projected = qualifyingStandingsService.projectedResultsFor(buggyClass,
                QualifyingStandingsService.Overlay.live(second.getId(), live));
        var before = qualifyingStandingsService.projectedResultsFor(buggyClass,
                QualifyingStandingsService.Overlay.without(second.getId()));

        assertThat(projected.results()).hasSize(20);
        assertThat(projected.results().get(0).entryId()).isEqualTo(secondGrid.get(0));
        assertThat(projected.results().get(0).points()).isEqualTo(1);
        assertThat(projected.waiting()).isEmpty();
        // Without the running heat only the finished one is ranked
        assertThat(before.results()).extracting(r -> r.entryId()).containsExactlyInAnyOrderElementsOf(firstGrid);

        // With the first heat the one on track and the second still to run, the second heat's drivers wait
        List<LiveTimingRowDto> firstLive = new ArrayList<>();
        for (int i = 0; i < firstGrid.size(); i++) {
            firstLive.add(new LiveTimingRowDto(firstGrid.get(i), "d", "1", i + 1, 5, 0L, null, null, null, null,
                    0, 0, null, null, null, false, 0L));
        }
        jdbc.update("delete from result_snapshots where race_id = ?", first.getId());
        jdbc.update("update races set status = 'RUNNING' where id = ?", first.getId());
        var early = qualifyingStandingsService.projectedResultsFor(buggyClass,
                QualifyingStandingsService.Overlay.live(first.getId(), firstLive));

        assertThat(early.results()).extracting(r -> r.entryId()).containsExactlyInAnyOrderElementsOf(firstGrid);
        assertThat(early.waiting()).containsExactlyInAnyOrderElementsOf(secondGrid);
    }

    @Test
    void theRoundTableRanksTheHeatsOfOneRoundByLapsAndLeavesOutDriversWhoseHeatHasNotRun() {
        long buggyClass = addClass("Buggy", ROUND_BY_ROUND_ALL);
        addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        List<Race> heats = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER);
        Race first = heats.get(0);
        Race second = heats.get(1);
        List<Long> firstGrid = raceEntryRepository.findByRaceIdOrderByGridPosition(first.getId()).stream()
                .map(RaceEntry::getEntryId).toList();
        List<Long> secondGrid = raceEntryRepository.findByRaceIdOrderByGridPosition(second.getId()).stream()
                .map(RaceEntry::getEntryId).toList();
        List<long[]> rows = new ArrayList<>();
        firstGrid.forEach(id -> rows.add(new long[] {id, 10, 60_000}));
        storeResult(first, rows, false);
        List<LiveTimingRowDto> live = new ArrayList<>();
        for (int i = 0; i < secondGrid.size(); i++) {
            int laps = i == 0 ? 12 : 9;
            live.add(new LiveTimingRowDto(secondGrid.get(i), "d", "1", i + 1, laps, 0L, 14_000L, 13_000L, 14_000L,
                    13_000L, 0, 0, null, null, 300_000L + i, false, 0L));
        }

        var round = qualifyingStandingsService.projectedRoundFor(buggyClass, first.getRoundId(),
                QualifyingStandingsService.Overlay.live(second.getId(), live));
        var before = qualifyingStandingsService.projectedRoundFor(buggyClass, first.getRoundId(),
                QualifyingStandingsService.Overlay.without(second.getId()));

        assertThat(round).hasSize(20);
        assertThat(round.get(0).entryId()).isEqualTo(secondGrid.get(0));
        assertThat(round.get(0).lapsCompleted()).isEqualTo(12);
        assertThat(before).extracting(r -> r.entryId()).containsExactlyInAnyOrderElementsOf(firstGrid);
    }

    @Test
    void roundByRoundQualifyingSharesAPlaceOnAnExactTieAndSkipsARoundWhoseHeatsWereAllAbandoned() {
        long buggyClass = addClass("Buggy", ROUND_BY_ROUND_ALL);
        List<Long> drivers = addEntries(buggyClass, 3);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 2, 10, List.of(
                new ClassFinalsConfig(buggyClass, 1))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            int round = jdbc.queryForObject("select round_number from rounds where id = ?", Integer.class,
                    heat.getRoundId());
            List<long[]> rows = new ArrayList<>();
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                // Round 1: drivers 0 and 1 tie on laps and time, driver 2 is behind. Round 2 is abandoned
                int laps = drivers.indexOf(onGrid.getEntryId()) == 2 ? 8 : 10;
                rows.add(new long[] {onGrid.getEntryId(), laps, 60_000});
            }
            storeResult(heat, rows, round == 2);
        }

        var ranked = qualifyingStandingsService.rankedResultsFor(buggyClass);

        assertThat(ranked).extracting(r -> r.points()).containsExactly(1, 1, 3);
        assertThat(ranked.get(2).entryId()).isEqualTo(drivers.get(2));
    }

    @Test
    void fillingTheFinalsFromTheTopGivesATenCarAFinalAndAShortLowestFinal() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 25);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 3))));

        bumpUpSeedingService.seedFinals(buggyClass, buggies, 3, 10, 0, true);

        assertThat(gridOf(finalOf(buggyClass, "A"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(buggies.subList(0, 10));
        assertThat(gridOf(finalOf(buggyClass, "B"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(buggies.subList(10, 20));
        assertThat(gridOf(finalOf(buggyClass, "C"))).extracting(RaceEntry::getEntryId)
                .containsExactlyElementsOf(buggies.subList(20, 25));
        assertThat(finalOf(buggyClass, "A").getBumpSlots()).isZero();
    }

    private static final String POINTS_FINALS_LEGS = """
            {"type":"POINTS_FINALS","qualifyingHeats":2,"finalsCount":2,"finalDurationMinutes":5,
             "heatDurationMinutes":5,"qualifyingStartType":"STAGGER","finalsStartType":"GRID",
             "qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5,
             "fillTopFirst":true,"carsPerFinal":6,"finalLegs":3,"legsCounted":2}""";

    @Test
    void theAFinalIsRunInLegsThatShareOneGridAndAreCombinedBestTwoOfThree() {
        long buggyClass = addClass("Buggy", POINTS_FINALS_LEGS);
        List<Long> drivers = addEntries(buggyClass, 12);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 12, List.of(
                new ClassFinalsConfig(buggyClass, 2))));

        List<Race> aLegs = raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.FINAL).stream()
                .filter(f -> "A".equals(f.getFinalLetter()))
                .sorted(Comparator.comparingInt(Race::getHeatNumber)).toList();
        assertThat(aLegs).extracting(Race::getHeatNumber).containsExactly(1, 2, 3);
        assertThat(aLegs.stream().map(Race::getRoundId).distinct()).hasSize(1);
        assertThat(raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.FINAL)).hasSize(4);

        bumpUpSeedingService.seedFinals(buggyClass, drivers, 2, 6, 0, true);

        List<Long> expected = drivers.subList(0, 6);
        for (Race leg : aLegs) {
            assertThat(gridOf(leg)).extracting(RaceEntry::getEntryId).containsExactlyElementsOf(expected);
        }
        assertThat(finalLegsService.forEvent(eventId).get(0).complete()).isFalse();

        int[][] lapsByLeg = {{12, 10, 10, 10, 10, 10}, {10, 12, 10, 10, 10, 10}, {5, 11, 10, 10, 10, 10}};
        for (int leg = 0; leg < 3; leg++) {
            List<long[]> rows = new ArrayList<>();
            for (int d = 0; d < 6; d++) {
                rows.add(new long[] {expected.get(d), lapsByLeg[leg][d], 60_000});
            }
            storeResult(aLegs.get(leg), rows, false);
        }

        var combined = finalLegsService.forEvent(eventId);

        assertThat(combined).hasSize(1);
        assertThat(combined.get(0).complete()).isTrue();
        assertThat(combined.get(0).legsFinished()).isEqualTo(3);
        assertThat(combined.get(0).rows()).extracting(r -> r.entryId())
                .startsWith(expected.get(1), expected.get(0));
        assertThat(combined.get(0).rows().get(0).points()).isEqualTo(2);
    }

    private static final String POINTS_FINALS_BRCA = """
            {"type":"POINTS_FINALS","qualifyingHeats":2,"finalsCount":1,"finalDurationMinutes":5,
             "heatDurationMinutes":5,"qualifyingStartType":"STAGGER","finalsStartType":"GRID",
             "qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5,
             "bestHeatsCount":0,"splitTiedPlaces":true,"brcaQualifying":true}""";

    @Test
    void brcaQualifyingGivesEachCarItsOwnPlaceAndAFlatScoreToADriverWhoDidNotRun() {
        long buggyClass = addClass("Buggy", POINTS_FINALS_BRCA);
        List<Long> drivers = addEntries(buggyClass, 4);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 1))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            List<long[]> rows = new ArrayList<>();
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                // Drivers 0 and 1 tie on laps and time, driver 2 is behind and driver 3 never started
                int index = drivers.indexOf(onGrid.getEntryId());
                rows.add(new long[] {onGrid.getEntryId(), index == 3 ? 0 : index == 2 ? 8 : 10, 60_000});
            }
            storeResult(heat, rows, false);
        }

        var ranked = qualifyingStandingsService.rankedResultsFor(buggyClass);

        // A tie does not share a place, and the driver with no laps scores the 4 entries plus one
        assertThat(ranked).extracting(r -> r.points()).containsExactly(1, 2, 3, 5);
        assertThat(ranked.get(3).entryId()).isEqualTo(drivers.get(3));
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
        runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 11, 2);
        Race aFinal = finalOf(buggyClass, "A");
        int seeded = gridOf(aFinal).size();

        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 10, 11))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("update races set status = 'RUNNING' where id = ?", aFinal.getId());
        assertThatThrownBy(() -> runOrderService.seedFinals(Actor.system("test"), eventId, buggyClass, 2, 11, 2))
                .isInstanceOf(StateConflictException.class);

        assertThat(gridOf(aFinal)).hasSize(seeded);
    }

    @Test
    void finalsAreCalculatedOnRequestThenEditedByMovesAndSwapsUntilAFinalIsCalled() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));

        var before = finalsLineupService.forEvent(eventId).get(0);
        assertThat(before.qualifyingComplete()).isFalse();
        assertThat(before.calculated()).isFalse();
        assertThat(before.defaultCarsPerFinal()).isEqualTo(10);

        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            finishWithResult(heat, buggies, 1);
        }
        // Finishing the last heat does not seed anything by itself
        assertThat(finalsLineupService.forEvent(eventId).get(0).calculated()).isFalse();
        assertThat(finalsLineupService.forEvent(eventId).get(0).qualifyingComplete()).isTrue();

        autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass, 12);
        var lineup = finalsLineupService.forEvent(eventId).get(0);
        assertThat(lineup.calculated()).isTrue();
        assertThat(lineup.finals()).extracting(f -> f.letter()).containsExactly("A", "B");
        Race a = finalOf(buggyClass, "A");
        Race b = finalOf(buggyClass, "B");
        long topB = gridOf(b).get(0).getEntryId();
        long topA = gridOf(a).get(0).getEntryId();

        // The finals need not stay the same size: 12 cars each calculated 8 in A and 12 in B, and a move makes it 9 and 11
        finalsLineupService.moveDriver(Actor.system("test"), b.getId(), topB, a.getId(), 1);
        assertThat(gridOf(a)).extracting(RaceEntry::getEntryId).first().isEqualTo(topB);
        assertThat(gridOf(a)).extracting(RaceEntry::getGridPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, gridOf(a).size()).boxed().toList());
        assertThat(gridOf(b)).extracting(RaceEntry::getEntryId).doesNotContain(topB);

        // A swap across finals exchanges the two drivers and their places
        long lastB = gridOf(b).get(gridOf(b).size() - 1).getEntryId();
        finalsLineupService.swapDrivers(Actor.system("test"), a.getId(), topA, b.getId(), lastB);
        assertThat(gridOf(a)).extracting(RaceEntry::getEntryId).contains(lastB).doesNotContain(topA);
        assertThat(gridOf(b)).extracting(RaceEntry::getEntryId).contains(topA).doesNotContain(lastB);

        // Once a final is called, its line-up is locked
        jdbc.update("update races set status = 'GRID' where id = ?", a.getId());
        assertThatThrownBy(() -> finalsLineupService.moveDriver(Actor.system("test"), b.getId(),
                gridOf(b).get(0).getEntryId(), a.getId(), null)).isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> autoFinalsSeedingService.reseed(Actor.system("test"), eventId, buggyClass, null))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void theLineupScreenIsRequiredOnceQualifyingEndsUntilConfirmedAndComesBackWhenFinalsAreCalculatedAgain() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        Actor actor = Actor.system("test");

        // Qualifying still running: no screen, and nothing to confirm
        assertThat(finalsLineupService.review(eventId).required()).isFalse();
        assertThatThrownBy(() -> finalsLineupService.confirm(actor, eventId)).isInstanceOf(StateConflictException.class);

        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            finishWithResult(heat, buggies, 1);
        }
        // Qualifying is over: the screen is required, but the line-ups must be calculated before they are confirmed
        assertThat(finalsLineupService.review(eventId).required()).isTrue();
        assertThatThrownBy(() -> finalsLineupService.confirm(actor, eventId)).isInstanceOf(StateConflictException.class);

        autoFinalsSeedingService.reseed(actor, eventId, buggyClass, null);
        var confirmed = finalsLineupService.confirm(actor, eventId);
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(finalsLineupService.review(eventId).required()).isFalse();
        assertThat(finalsLineupService.confirm(actor, eventId).confirmedAt()).isEqualTo(confirmed.confirmedAt());

        // Calculating again replaces the line-ups, so they have to be confirmed again
        autoFinalsSeedingService.reseed(actor, eventId, buggyClass, null);
        assertThat(finalsLineupService.review(eventId).required()).isTrue();
        assertThat(finalsLineupService.review(eventId).confirmedAt()).isNull();

        // A final called to the grid locks the line-ups and ends the screen
        jdbc.update("update races set status = 'GRID' where id = ?", finalOf(buggyClass, "A").getId());
        assertThat(finalsLineupService.review(eventId).required()).isFalse();
        assertThatThrownBy(() -> finalsLineupService.confirm(actor, eventId)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void calculatingAllClassesUsesTheCarsPerFinalTheOfficialSetForAClass() {
        long buggyClass = addClass("Buggy");
        List<Long> buggies = addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            finishWithResult(heat, buggies, 1);
        }
        autoFinalsSeedingService.reseedAll(Actor.system("test"), eventId, Map.of(buggyClass, 12));
        // Finals fill from the top, so the lowest final is the short one
        assertThat(gridOf(finalOf(buggyClass, "A"))).hasSize(12);
        assertThat(gridOf(finalOf(buggyClass, "B"))).hasSize(8);
        assertThatThrownBy(() -> autoFinalsSeedingService.reseedAll(Actor.system("test"), eventId,
                Map.of(buggyClass, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLineupScreenIsNotRequiredWhenEveryQualifyingHeatWasAbandoned() {
        long buggyClass = addClass("Buggy");
        addEntries(buggyClass, 20);
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 10, List.of(
                new ClassFinalsConfig(buggyClass, 2))));
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(buggyClass, RoundType.QUALIFIER)) {
            jdbc.update("update races set status = 'FINISHED', abandoned_at = 1 where id = ?", heat.getId());
        }
        assertThat(finalsLineupService.review(eventId).required()).isFalse();
        assertThatThrownBy(() -> finalsLineupService.confirm(Actor.system("test"), eventId))
                .isInstanceOf(StateConflictException.class);
    }

    private long addClass(String name) {
        return addClass(name, "{\"type\":\"TIMED\"}");
    }

    private long addClass(String name, String configSnapshot) {
        long racingClassId = jdbc.queryForObject(
                "insert into racing_classes (name) values (?) returning id", Long.class, name + " " + run);
        racingClassIds.add(racingClassId);
        return jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, ?) returning id""", Long.class, eventId, racingClassId, configSnapshot);
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
