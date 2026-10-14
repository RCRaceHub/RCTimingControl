package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.championship.Championship;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLink;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLinkRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleEntry;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipRepository;
import dev.monkeypatch.rctiming.domain.championship.ScoringSource;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ChampionshipStandingsQuery.
 * Tests: best-X-from-Y marks correct rounds as dropped (CHAMP-05);
 *        TQ bonus and A-final winner bonus applied correctly (CHAMP-07/08);
 *        DNS driver (entered but absent from positions_json) scores 0 and round counts toward Y.
 */
class ChampionshipStandingsQueryTest extends AbstractIntegrationTest {

    @Autowired
    ChampionshipStandingsQuery query;

    @Autowired
    ChampionshipRepository championshipRepository;

    @Autowired
    ChampionshipEventLinkRepository eventLinkRepository;

    @Autowired
    ChampionshipPointsScaleRepository pointsScaleRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    CompetitorService competitorService;

    @Autowired
    CompetitorRepository competitorRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    RaceEntryRepository raceEntryRepository;

    @Autowired
    ResultSnapshotRepository resultSnapshotRepository;

    @Autowired
    DSLContext dsl;

    private Long racingClassId;

    /**
     * Create a user for testing. Email is randomised to avoid unique constraint violations
     * across tests sharing the same SQLite test database.
     */
    private User makeUser(String firstName, String lastName) {
        User u = new User();
        u.setEmail(firstName.toLowerCase() + "-" + UUID.randomUUID() + "@standings-test.com");
        u.setPasswordHash(passwordEncoder.encode("pass"));
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setRoles(Set.of());
        Instant now = Instant.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return userRepository.save(u);
    }

    private Championship makeChampionship(int tqBonus, int afinalBonus,
                                           Integer bestX, Integer bestY,
                                           ScoringSource source) {
        Championship c = new Championship();
        c.setName("Test Champ " + UUID.randomUUID());
        c.setTqBonusPoints(tqBonus);
        c.setAfinalWinnerBonusPoints(afinalBonus);
        c.setBestXFromYX(bestX);
        c.setBestXFromYY(bestY);
        c.setScoringSource(source);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return championshipRepository.save(c);
    }

    private Event makeEvent(String name) {
        Event e = new Event();
        e.setName(name);
        e.setEventDate(LocalDate.of(2026, 1, 1));
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return eventRepository.save(e);
    }

    private ChampionshipEventLink linkEventToChampionship(Long champId, Long eventId, int roundNum) {
        ChampionshipEventLink link = new ChampionshipEventLink();
        link.setChampionshipId(champId);
        link.setEventId(eventId);
        link.setRoundNumber(roundNum);
        link.setCreatedAt(Instant.now());
        return eventLinkRepository.save(link);
    }

    private void addPointsScale(Long champId, Map<Integer, Integer> scale) {
        scale.forEach((pos, pts) ->
            pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champId, pos, pts)));
    }

    /**
     * Inserts an event_class row directly with jOOQ.
     * config_snapshot is NOT NULL but not used in standings logic.
     */
    private Long makeEventClass(Long eventId) {
        return dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID)
                .fetchOne()
                .get(EVENT_CLASSES.ID));
    }

    private Round makeRound(Long eventId, RoundType type, int roundNum) {
        Round r = new Round();
        r.setEventId(eventId);
        r.setType(type);
        r.setRoundNumber(roundNum);
        r.setSequenceInEvent(roundNum);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return roundRepository.save(r);
    }

    private Race makeRace(Long roundId, Long eventClassId, String finalLetter, int heatNum) {
        Race race = new Race();
        race.setRoundId(roundId);
        race.setEventClassId(eventClassId);
        race.setHeatNumber(heatNum);
        race.setSequenceInRound(heatNum);
        race.setFinalLetter(finalLetter);
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.FINISHED);
        Instant now = Instant.now();
        race.setCreatedAt(now);
        race.setUpdatedAt(now);
        race.setFinishedAt(now);
        return raceRepository.save(race);
    }

    private final Map<Long, Long> competitorIdByUserId = new HashMap<>();

    /** One walk-in competitor per test user, named after the user. */
    private Long competitorIdFor(Long userId) {
        return competitorIdByUserId.computeIfAbsent(userId, id -> {
            User u = userRepository.findById(id).orElseThrow();
            return competitorService.createWalkIn(u.getFirstName() + " " + u.getLastName()).getId();
        });
    }

    private Entry makeEntry(Long userId, Long eventId, Long eventClassId) {
        Entry e = makeCompetitorEntry(competitorIdFor(userId), eventId, eventClassId);
        e.setUserId(userId);
        return entryRepository.save(e);
    }

    /** An entry with a competitor and no login user, as a RaceHub import or walk-in creates. */
    private Entry makeCompetitorEntry(Long competitorId, Long eventId, Long eventClassId) {
        Entry e = new Entry();
        e.setCompetitorId(competitorId);
        e.setEventId(eventId);
        e.setEventClassId(eventClassId);
        e.setTransponderNumberSnapshot("T-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(EntryStatus.CONFIRMED);
        Instant now = Instant.now();
        e.setSubmittedAt(now);
        e.setUpdatedAt(now);
        return entryRepository.save(e);
    }

    @Autowired
    EntryRepository entryRepository;

    private RaceEntry makeRaceEntry(Long raceId, Long entryId) {
        RaceEntry re = new RaceEntry();
        re.setRaceId(raceId);
        re.setEntryId(entryId);
        return raceEntryRepository.save(re);
    }

    private ResultSnapshot makeSnapshot(Long raceId, String positionsJson) {
        ResultSnapshot snap = new ResultSnapshot();
        snap.setRaceId(raceId);
        snap.setPositionsJson(positionsJson);
        snap.setLapHistoryJson("[]");
        snap.setFinishedAt(Instant.now());
        snap.setCreatedAt(Instant.now());
        return resultSnapshotRepository.save(snap);
    }

    @BeforeEach
    void setUp() {
        RacingClass rc = new RacingClass();
        rc.setName("GT12-" + UUID.randomUUID());
        Instant now = Instant.now();
        rc.setCreatedAt(now);
        rc.setUpdatedAt(now);
        racingClassId = racingClassRepository.save(rc).getId();
    }

    @Test
    void aResultInAClassWithNoRacingClassStillGivesStandings() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        User driver = makeUser("No", "Class");
        addPointsScale(champ.getId(), Map.of(1, 10));

        Event event = makeEvent("No-racing-class-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, event.getId())
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race race = makeRace(round.getId(), ecId, "A", 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(race.getId(), entry.getId());
        makeSnapshot(race.getId(), String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"No Class\",\"carNumber\":\"1\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0}]",
                entry.getId()));

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).totalPoints()).isEqualTo(10);
    }

    @Test
    void bestXFromYDropsWorstRounds() throws Exception {
        // Championship: best 2 from 3 (drop worst 1 of 3 rounds)
        Championship champ = makeChampionship(0, 0, 2, 3, ScoringSource.FINALS);
        User driver = makeUser("Alice", "Drop");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6, 4, 4));

        // Round 1: Alice finishes 1st (10 pts)
        // Round 2: Alice finishes 2nd (8 pts)
        // Round 3: Alice finishes 4th (4 pts) ← worst, should be dropped
        int[] positions = {1, 2, 4};

        for (int rn = 1; rn <= 3; rn++) {
            int position = positions[rn - 1];
            Event event = makeEvent("Event-drop-" + rn);
            linkEventToChampionship(champ.getId(), event.getId(), rn);
            Long ecId = makeEventClass(event.getId());
            Round round = makeRound(event.getId(), RoundType.FINAL, 1);
            Race race = makeRace(round.getId(), ecId, "A", 1);
            Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
            makeRaceEntry(race.getId(), entry.getId());

            String posJson = String.format(
                    "[{\"position\":%d,\"entryId\":%d,\"driverName\":\"Alice\",\"carNumber\":\"1\","
                    + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0}]",
                    position, entry.getId());
            makeSnapshot(race.getId(), posJson);
        }

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());
        assertThat(standings).hasSize(1);

        StandingsRowDto row = standings.get(0);
        assertThat(row.driverId()).isEqualTo(competitorIdFor(driver.getId()));
        assertThat(row.displayName()).isEqualTo("Alice Drop");
        // Best 2 from 3: 10 + 8 = 18 (4 pts dropped)
        assertThat(row.totalPoints()).isEqualTo(18);

        long droppedCount = row.rounds().stream().filter(RoundResultDto::dropped).count();
        assertThat(droppedCount).isEqualTo(1);

        // The dropped round should have 4 points
        RoundResultDto droppedRound = row.rounds().stream()
                .filter(RoundResultDto::dropped)
                .findFirst()
                .orElseThrow();
        assertThat(droppedRound.points()).isEqualTo(4);
    }

    @Test
    void tqBonusApplied() throws Exception {
        // Championship with tqBonus=2, scoring QUALIFYING
        Championship champ = makeChampionship(2, 0, null, null, ScoringSource.QUALIFYING);
        User driver = makeUser("TQ", "Driver");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("TQ-test-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round qualRound = makeRound(event.getId(), RoundType.QUALIFIER, 1);
        Race qualRace = makeRace(qualRound.getId(), ecId, null, 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(qualRace.getId(), entry.getId());

        // Driver finishes 1st in qualifier → gets TQ bonus
        String posJson = String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"TQ\",\"carNumber\":\"7\","
                + "\"lapsCompleted\":12,\"totalTimeMs\":60000,\"bestLapMs\":5000,\"gapToLeaderMs\":0}]",
                entry.getId());
        makeSnapshot(qualRace.getId(), posJson);

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());
        assertThat(standings).hasSize(1);

        StandingsRowDto row = standings.get(0);
        // 10 (1st place) + 2 (TQ bonus) = 12
        assertThat(row.totalPoints()).isEqualTo(12);
    }

    @Test
    void winningTwoQualifyingHeatsInOneClassAtOneEventPaysTheTqBonusOnce() throws Exception {
        // Two heats of the same class in one qualifying round, both won by the same driver (#133)
        Championship champ = makeChampionship(2, 0, null, null, ScoringSource.QUALIFYING);
        User driver = makeUser("Heat", "Winner");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Two-heats-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round qualRound = makeRound(event.getId(), RoundType.QUALIFIER, 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        for (int heat = 1; heat <= 2; heat++) {
            Race race = makeRace(qualRound.getId(), ecId, null, heat);
            makeRaceEntry(race.getId(), entry.getId());
            makeSnapshot(race.getId(), String.format(
                    "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Heat\",\"carNumber\":\"7\","
                    + "\"lapsCompleted\":12,\"totalTimeMs\":60000,\"bestLapMs\":5000,\"gapToLeaderMs\":0}]",
                    entry.getId()));
        }

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).hasSize(1);
        // 10 (best qualifying position) + 2 (one TQ bonus for this class and round, not one per heat)
        assertThat(standings.get(0).totalPoints()).isEqualTo(12);
    }

    @Test
    void anExcludedDriverEarnsNoAFinalBonusForTheRound() throws Exception {
        Championship champ = makeChampionship(0, 3, null, null, ScoringSource.FINALS);
        User driver = makeUser("Excluded", "Winner");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Excluded-winner-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(aFinal.getId(), entry.getId());
        makeSnapshot(aFinal.getId(), String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Excluded\",\"carNumber\":\"42\","
                + "\"lapsCompleted\":15,\"totalTimeMs\":65000,\"bestLapMs\":4300,\"gapToLeaderMs\":0}]",
                entry.getId()));
        dsl.transactionResult(tx -> tx.dsl().insertInto(CHAMPIONSHIP_EXCLUSIONS)
                .set(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID, champ.getId())
                .set(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID, competitorIdFor(driver.getId()))
                .set(CHAMPIONSHIP_EXCLUSIONS.EVENT_ID, event.getId())
                .set(CHAMPIONSHIP_EXCLUSIONS.REASON, "Non-eligible equipment")
                .set(CHAMPIONSHIP_EXCLUSIONS.CREATED_BY, driver.getId())
                .execute());

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).hasSize(1);
        // Excluded from the round: no finishing points and no A-final bonus
        assertThat(standings.get(0).totalPoints()).isZero();
        assertThat(standings.get(0).rounds().get(0).excluded()).isTrue();
    }

    @Test
    void afinalWinnerBonusApplied() throws Exception {
        // Championship with afinalBonus=3, scoring FINALS
        Championship champ = makeChampionship(0, 3, null, null, ScoringSource.FINALS);
        User driver = makeUser("AFinal", "Winner");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("AFinal-test-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinalRace = makeRace(finalRound.getId(), ecId, "A", 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(aFinalRace.getId(), entry.getId());

        // Driver finishes 1st in A-final
        String posJson = String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"AFinal\",\"carNumber\":\"42\","
                + "\"lapsCompleted\":15,\"totalTimeMs\":65000,\"bestLapMs\":4300,\"gapToLeaderMs\":0}]",
                entry.getId());
        makeSnapshot(aFinalRace.getId(), posJson);

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());
        assertThat(standings).hasSize(1);

        StandingsRowDto row = standings.get(0);
        // 10 (1st place) + 3 (A-final winner bonus) = 13
        assertThat(row.totalPoints()).isEqualTo(13);
    }

    @Test
    void dnsDriverScoresZeroAndRoundCountsTowardY() throws Exception {
        // Championship: best 1 from 2 (drop worst of 2)
        Championship champ = makeChampionship(0, 0, 1, 2, ScoringSource.FINALS);
        User driver = makeUser("DNS", "Driver");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        // Event 1: driver finishes 2nd (8 pts)
        Event event1 = makeEvent("DNS-Event-1");
        linkEventToChampionship(champ.getId(), event1.getId(), 1);
        Long ecId1 = makeEventClass(event1.getId());
        Round round1 = makeRound(event1.getId(), RoundType.FINAL, 1);
        Race race1 = makeRace(round1.getId(), ecId1, "A", 1);
        Entry entry1 = makeEntry(driver.getId(), event1.getId(), ecId1);
        makeRaceEntry(race1.getId(), entry1.getId());

        String posJson1 = String.format(
                "[{\"position\":2,\"entryId\":%d,\"driverName\":\"DNS\",\"carNumber\":\"5\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":70000,\"bestLapMs\":7000,\"gapToLeaderMs\":1000}]",
                entry1.getId());
        makeSnapshot(race1.getId(), posJson1);

        // Event 2: driver is in race_entries but NOT in positions_json → DNS (0 pts)
        Event event2 = makeEvent("DNS-Event-2");
        linkEventToChampionship(champ.getId(), event2.getId(), 2);
        Long ecId2 = makeEventClass(event2.getId());
        Round round2 = makeRound(event2.getId(), RoundType.FINAL, 1);
        Race race2 = makeRace(round2.getId(), ecId2, "A", 1);
        Entry entry2 = makeEntry(driver.getId(), event2.getId(), ecId2);
        makeRaceEntry(race2.getId(), entry2.getId());

        // Another driver wins event2; DNS driver is entered but absent from positions_json
        User otherDriver = makeUser("Other", "Driver");
        Entry otherEntry = makeEntry(otherDriver.getId(), event2.getId(), ecId2);
        makeRaceEntry(race2.getId(), otherEntry.getId());

        String posJson2 = String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Other\",\"carNumber\":\"9\","
                + "\"lapsCompleted\":11,\"totalTimeMs\":65000,\"bestLapMs\":5900,\"gapToLeaderMs\":0}]",
                otherEntry.getId());
        makeSnapshot(race2.getId(), posJson2);

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // DNS driver should appear
        StandingsRowDto dnsDriverRow = standings.stream()
                .filter(r -> r.driverId().equals(competitorIdFor(driver.getId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DNS driver not found in standings"));

        // 2 rounds (Y=2), best 1 (X=1) — drop worst 1 of 2
        assertThat(dnsDriverRow.rounds()).hasSize(2);

        // At least 1 round should be DNS (position=0, points=0)
        long dnsRounds = dnsDriverRow.rounds().stream()
                .filter(r -> r.position() == 0 && r.points() == 0)
                .count();
        assertThat(dnsRounds).isGreaterThanOrEqualTo(1);

        // Total = best 1 from 2 = 8 pts (DNS round with 0 pts is dropped since 0 < 8)
        assertThat(dnsDriverRow.totalPoints()).isEqualTo(8);
    }

    @Test
    void competitorWithNoUserAppearsInStandings() {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Competitor walkIn = new Competitor();
        walkIn.setDisplayName("Wendy Walkin");
        walkIn.setExternalSource("RACEHUB");
        walkIn.setExternalId("driver-" + UUID.randomUUID());
        walkIn.setCreatedAt(Instant.now());
        walkIn.setUpdatedAt(Instant.now());
        walkIn = competitorRepository.save(walkIn);
        User loginDriver = makeUser("Larry", "Login");

        Event event = makeEvent("Walk-in event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race race = makeRace(round.getId(), ecId, "A", 1);
        Entry walkInEntry = makeCompetitorEntry(walkIn.getId(), event.getId(), ecId);
        Entry loginEntry = makeEntry(loginDriver.getId(), event.getId(), ecId);
        makeRaceEntry(race.getId(), walkInEntry.getId());
        makeRaceEntry(race.getId(), loginEntry.getId());

        makeSnapshot(race.getId(), String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Wendy Walkin\",\"carNumber\":\"1\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0},"
                + "{\"position\":2,\"entryId\":%d,\"driverName\":\"Larry Login\",\"carNumber\":\"2\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":61000,\"bestLapMs\":6100,\"gapToLeaderMs\":1000}]",
                walkInEntry.getId(), loginEntry.getId()));

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Wendy Walkin", 10),
                        org.assertj.core.groups.Tuple.tuple("Larry Login", 8));
        assertThat(standings.get(0).driverId()).isEqualTo(walkIn.getId());
    }
}
