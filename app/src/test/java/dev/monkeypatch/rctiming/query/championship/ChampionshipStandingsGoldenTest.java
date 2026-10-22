package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.championship.Championship;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipClass;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipClassRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLink;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLinkRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleEntry;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipRepository;
import dev.monkeypatch.rctiming.domain.championship.ScoringSource;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test for championship standings (L5, #13).
 *
 * <p>Builds a two-class, three-round championship that exercises best-X-from-Y drops, TQ and
 * A-final bonuses, DNS rounds, an exclusion and a driver racing in two classes. The expected
 * output in {@code golden/championship-standings.txt} was first captured from the user-keyed
 * implementation before standings switched to competitors, which proved the switch left the
 * standings unchanged. It was updated for #133, when the TQ and A-final bonuses became per class
 * per round (CHAMP-07, CHAMP-08): Alice earns a bonus in both classes, Dan earns the Stock A-final
 * bonus in two rounds, and Bob's excluded round earns no TQ bonus. The base points per round did not change.
 * Scoring from both sources (CHAMP-06) sums the qualifying and final points of a round, and the TQ goes to one
 * driver per class and round (CHAMP-07), so the main and heats outputs changed in #248. Each result's best lap is
 * a little slower for each place, so the fastest qualifier is the one who finished first.
 */
class ChampionshipStandingsGoldenTest extends AbstractIntegrationTest {

    @Autowired ChampionshipStandingsQuery query;
    @Autowired ChampionshipRepository championshipRepository;
    @Autowired ChampionshipClassRepository championshipClassRepository;
    @Autowired ChampionshipEventLinkRepository eventLinkRepository;
    @Autowired ChampionshipPointsScaleRepository pointsScaleRepository;
    @Autowired UserRepository userRepository;
    @Autowired CompetitorService competitorService;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired ResultSnapshotRepository resultSnapshotRepository;
    @Autowired DSLContext dsl;

    /** A driver in the fixture: the login and the competitor that stands in for it. */
    private record Driver(User user, Competitor competitor) {}

    @Test
    void standingsMatchGoldenOutput() throws IOException {
        Fixture f = buildChampionship(ScoringSource.BOTH);

        assertThat(render(query.computeStandings(f.champId()), f.classNames()))
                .isEqualTo(readGolden("championship-standings.txt"));
    }

    /**
     * The same rounds scored from the finals only, with a class override that sets only best X.
     * The TQ bonus still comes from the qualifiers. Stock keeps its best round from three (Y falls
     * back to the championship's), and Dan's two equal wins drop the later one.
     */
    @Test
    void finalsOnlyWithAClassOverrideMatchGoldenOutput() throws IOException {
        Fixture f = buildChampionship(ScoringSource.FINALS);
        ChampionshipClass stockRule = new ChampionshipClass();
        stockRule.setChampionshipId(f.champId());
        stockRule.setRacingClassId(f.stockClass());
        stockRule.setBestXFromYX(1);
        stockRule.setCreatedAt(Instant.now());
        championshipClassRepository.save(stockRule);
        // Only the classes added to a championship score, so Mod is added with no rule of its own
        ChampionshipClass modRule = new ChampionshipClass();
        modRule.setChampionshipId(f.champId());
        modRule.setRacingClassId(f.modClass());
        modRule.setCreatedAt(Instant.now());
        championshipClassRepository.save(modRule);

        assertThat(render(query.computeStandings(f.champId()), f.classNames()))
                .isEqualTo(readGolden("championship-standings-finals.txt"));
    }

    /**
     * One class with two qualifying rounds and an A final per event. Only the finals score, so a driver who was not
     * in the final has no round, and the qualifying heats still decide the TQ bonus. Dan and Finn's rivals Alice
     * and Finn tie on 15 with a win each, and stand by name.
     */
    @Test
    void qualifyingHeatsMatchGoldenOutput() throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long modClass = makeRacingClass("Golden Heats " + suffix);

        Championship champ = new Championship();
        champ.setName("Golden Heats Champ " + suffix);
        champ.setTqBonusPoints(2);
        champ.setAfinalWinnerBonusPoints(3);
        champ.setBestXFromYX(2);
        champ.setBestXFromYY(3);
        champ.setScoringSource(ScoringSource.QUALIFYING);
        champ.setCreatedAt(Instant.now());
        champ.setUpdatedAt(Instant.now());
        Long champId = championshipRepository.save(champ).getId();
        Map.of(1, 10, 2, 8, 3, 6, 4, 4).forEach((pos, pts) ->
                pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champId, pos, pts)));

        Driver alice = makeDriver("Alice", "Smith");
        Driver bob = makeDriver("Bob", "Jones");
        Driver cara = makeDriver("Cara", "Lee");
        Driver dan = makeDriver("Dan", "Moss");
        Driver eve = makeDriver("Eve", "Hart");
        Driver finn = makeDriver("Finn", "Reid");

        // Per round: who entered, the first qualifier's order, the second's, and the A final's
        List<List<List<Driver>>> rounds = List.of(
                List.of(List.of(alice, bob, cara, dan, eve), List.of(alice, bob, cara, dan, eve),
                        List.of(bob, alice, cara, dan), List.of(alice, bob, cara)),
                List.of(List.of(alice, bob, cara, dan, eve), List.of(cara, dan, eve, alice, bob),
                        List.of(cara, dan), List.of(dan, cara)),
                List.of(List.of(alice, bob, cara, dan, eve, finn), List.of(finn, alice, bob, cara),
                        List.of(finn, alice, bob, cara), List.of(finn)));

        for (int rn = 1; rn <= rounds.size(); rn++) {
            Event event = makeEvent("Golden Heats Round " + rn + " " + suffix);
            linkEvent(champId, event.getId(), rn);
            Long modEc = makeEventClass(event.getId(), modClass);
            List<List<Driver>> round = rounds.get(rn - 1);

            Map<Driver, Entry> entries = new LinkedHashMap<>();
            for (Driver d : round.get(0)) {
                entries.put(d, makeEntry(d, event.getId(), modEc));
            }
            Map<Driver, Entry> finalists = new LinkedHashMap<>();
            for (Driver d : round.get(3)) {
                finalists.put(d, entries.get(d));
            }

            Round q1 = makeRound(event.getId(), RoundType.QUALIFIER, 1);
            Round q2 = makeRound(event.getId(), RoundType.QUALIFIER, 2);
            Round fin = makeRound(event.getId(), RoundType.FINAL, 3);
            makeRaceWithResult(q1.getId(), modEc, null, entries, round.get(1));
            makeRaceWithResult(q2.getId(), modEc, null, entries, round.get(2));
            makeRaceWithResult(fin.getId(), modEc, "A", finalists, round.get(3));
        }

        assertThat(render(query.computeStandings(champId), Map.of(modClass, "Mod")))
                .isEqualTo(readGolden("championship-standings-heats.txt"));
    }

    /** The championship built by {@link #buildChampionship} and its two racing classes. */
    private record Fixture(Long champId, Long modClass, Long stockClass, Map<Long, String> classNames) {}

    private Fixture buildChampionship(ScoringSource scoringSource) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long modClass = makeRacingClass("Golden Mod " + suffix);
        Long stockClass = makeRacingClass("Golden Stock " + suffix);

        Championship champ = new Championship();
        champ.setName("Golden Champ " + suffix);
        champ.setTqBonusPoints(2);
        champ.setAfinalWinnerBonusPoints(3);
        champ.setBestXFromYX(2);
        champ.setBestXFromYY(3);
        champ.setScoringSource(scoringSource);
        champ.setCreatedAt(Instant.now());
        champ.setUpdatedAt(Instant.now());
        Long champId = championshipRepository.save(champ).getId();
        Map.of(1, 10, 2, 8, 3, 6, 4, 4).forEach((pos, pts) ->
                pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champId, pos, pts)));

        Driver alice = makeDriver("Alice", "Smith");
        Driver bob = makeDriver("Bob", "Jones");
        Driver cara = makeDriver("Cara", "Lee");
        Driver dan = makeDriver("Dan", "Moss");

        // Per round: Mod qualifier order, Mod A-final order, Stock A-final order.
        // A driver entered in a race but missing from its order did not start (DNS).
        List<List<List<Driver>>> rounds = List.of(
                List.of(List.of(alice, bob, cara), List.of(bob, alice, cara), List.of(dan, alice)),
                List.of(List.of(bob, cara, alice), List.of(cara, bob, alice), List.of(alice, dan)),
                List.of(List.of(alice, bob), List.of(alice, bob), List.of(dan)));

        for (int rn = 1; rn <= rounds.size(); rn++) {
            Event event = makeEvent("Golden Round " + rn + " " + suffix);
            linkEvent(champId, event.getId(), rn);
            Long modEc = makeEventClass(event.getId(), modClass);
            Long stockEc = makeEventClass(event.getId(), stockClass);

            Map<Driver, Entry> modEntries = new LinkedHashMap<>();
            for (Driver d : List.of(alice, bob, cara)) {
                modEntries.put(d, makeEntry(d, event.getId(), modEc));
            }
            Map<Driver, Entry> stockEntries = new LinkedHashMap<>();
            for (Driver d : List.of(alice, dan)) {
                stockEntries.put(d, makeEntry(d, event.getId(), stockEc));
            }

            List<List<Driver>> order = rounds.get(rn - 1);
            Round qual = makeRound(event.getId(), RoundType.QUALIFIER, 1);
            Round fin = makeRound(event.getId(), RoundType.FINAL, 2);
            makeRaceWithResult(qual.getId(), modEc, null, modEntries, order.get(0));
            makeRaceWithResult(fin.getId(), modEc, "A", modEntries, order.get(1));
            makeRaceWithResult(fin.getId(), stockEc, "A", stockEntries, order.get(2));

            if (rn == 2) {
                exclude(champId, bob, event.getId(), alice.user().getId());
            }
        }

        return new Fixture(champId, modClass, stockClass, Map.of(modClass, "Mod", stockClass, "Stock"));
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    private static String render(List<StandingsRowDto> rows, Map<Long, String> classNames) {
        List<String> lines = new ArrayList<>();
        for (StandingsRowDto row : rows) {
            String rounds = row.rounds().stream()
                    .map(r -> "R" + r.roundNumber() + ":" + r.position() + "/" + r.points()
                            + (r.excluded() ? "x" : "") + (r.dropped() ? "d" : ""))
                    .collect(Collectors.joining(" "));
            lines.add(classNames.get(row.racingClassId()) + " | " + nameOf(row)
                    + " | " + row.totalPoints() + " | " + rounds);
        }
        return String.join("\n", lines) + "\n";
    }

    private static String nameOf(StandingsRowDto row) {
        return row.displayName();
    }

    private String readGolden(String fileName) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/golden/" + fileName)) {
            assertThat(in).as("golden file").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────────

    private Driver makeDriver(String firstName, String lastName) {
        User u = new User();
        u.setEmail(firstName.toLowerCase() + "-" + UUID.randomUUID() + "@golden-test.com");
        u.setPasswordHash("x");
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setRoles(Set.of());
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);
        return new Driver(u, competitorService.createWalkIn(firstName + " " + lastName));
    }

    /** Excludes a driver from an event, keyed the way the standings query keys drivers. */
    private void exclude(Long champId, Driver driver, Long eventId, Long createdBy) {
        dsl.transactionResult(tx -> tx.dsl().insertInto(CHAMPIONSHIP_EXCLUSIONS)
                .set(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID, champId)
                .set(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID, driver.competitor().getId())
                .set(CHAMPIONSHIP_EXCLUSIONS.EVENT_ID, eventId)
                .set(CHAMPIONSHIP_EXCLUSIONS.REASON, "Golden exclusion")
                .set(CHAMPIONSHIP_EXCLUSIONS.CREATED_BY, createdBy)
                .execute());
    }

    private Long makeRacingClass(String name) {
        RacingClass rc = new RacingClass();
        rc.setName(name);
        rc.setCreatedAt(Instant.now());
        rc.setUpdatedAt(Instant.now());
        return racingClassRepository.save(rc).getId();
    }

    private Event makeEvent(String name) {
        Event e = new Event();
        e.setName(name);
        e.setEventDate(LocalDate.of(2026, 1, 1));
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return eventRepository.save(e);
    }

    private void linkEvent(Long champId, Long eventId, int roundNum) {
        ChampionshipEventLink link = new ChampionshipEventLink();
        link.setChampionshipId(champId);
        link.setEventId(eventId);
        link.setRoundNumber(roundNum);
        link.setCreatedAt(Instant.now());
        eventLinkRepository.save(link);
    }

    private Long makeEventClass(Long eventId, Long racingClassId) {
        return dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID)
                .fetchOne()
                .get(EVENT_CLASSES.ID));
    }

    private Round makeRound(Long eventId, RoundType type, int sequence) {
        Round r = new Round();
        r.setEventId(eventId);
        r.setType(type);
        r.setRoundNumber(1);
        r.setSequenceInEvent(sequence);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return roundRepository.save(r);
    }

    private Entry makeEntry(Driver driver, Long eventId, Long eventClassId) {
        Entry e = new Entry();
        e.setCompetitorId(driver.competitor().getId());
        e.setEventId(eventId);
        e.setEventClassId(eventClassId);
        e.setTransponderNumberSnapshot("G-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(EntryStatus.CONFIRMED);
        e.setSubmittedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return entryRepository.save(e);
    }

    /** Creates a finished race with every entry on the grid and a snapshot in the given finishing order. */
    private void makeRaceWithResult(Long roundId, Long eventClassId, String finalLetter,
                                    Map<Driver, Entry> entries, List<Driver> finishingOrder) {
        Race race = new Race();
        race.setRoundId(roundId);
        race.setEventClassId(eventClassId);
        race.setHeatNumber(1);
        race.setSequenceInRound((int) (raceRepository.count() % 1000) + 1);
        race.setFinalLetter(finalLetter);
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.FINISHED);
        race.setCreatedAt(Instant.now());
        race.setUpdatedAt(Instant.now());
        race.setFinishedAt(Instant.now());
        race = raceRepository.save(race);

        for (Entry entry : entries.values()) {
            RaceEntry re = new RaceEntry();
            re.setRaceId(race.getId());
            re.setEntryId(entry.getId());
            raceEntryRepository.save(re);
        }

        List<String> rows = new ArrayList<>();
        for (int i = 0; i < finishingOrder.size(); i++) {
            Driver d = finishingOrder.get(i);
            rows.add(String.format(
                    "{\"position\":%d,\"entryId\":%d,\"driverName\":\"%s\",\"carNumber\":\"%d\","
                    + "\"lapsCompleted\":10,\"totalTimeMs\":%d,\"bestLapMs\":%d,\"gapToLeaderMs\":%d}",
                    i + 1, entries.get(d).getId(), d.user().getFirstName(), i + 1,
                    60000 + i * 1000, 6000 + i * 100, i * 1000));
        }

        ResultSnapshot snap = new ResultSnapshot();
        snap.setRaceId(race.getId());
        snap.setPositionsJson("[" + String.join(",", rows) + "]");
        snap.setLapHistoryJson("[]");
        snap.setFinishedAt(Instant.now());
        snap.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snap);
    }
}
