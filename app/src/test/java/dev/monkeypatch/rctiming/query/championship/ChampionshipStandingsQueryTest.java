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
    ChampionshipSeedingQuery seedingQuery;

    @Autowired
    ChampionshipRepository championshipRepository;

    @Autowired
    ChampionshipClassRepository championshipClassRepository;

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
    void aShikabaneClassNeverScoresInAChampionship() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        User driver = makeUser("Fun", "Day");
        addPointsScale(champ.getId(), Map.of(1, 10));

        Event event = makeEvent("Shikabane-fun-day");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, event.getId())
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"SHIKABANE\",\"startType\":\"GRID\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race race = makeRace(round.getId(), ecId, "A", 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(race.getId(), entry.getId());
        makeSnapshot(race.getId(), String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Fun Day\",\"carNumber\":\"1\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0}]",
                entry.getId()));

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings.stream().mapToInt(StandingsRowDto::totalPoints).sum()).isZero();
    }

    /** One finished A final at a new event linked as round {@code roundNum}, with a driver in the given place. */
    private void finishRound(Championship champ, int roundNum, User driver, int position) {
        Event event = makeEvent("Event-" + roundNum + "-" + UUID.randomUUID().toString().substring(0, 6));
        linkEventToChampionship(champ.getId(), event.getId(), roundNum);
        Long ecId = makeEventClass(event.getId());
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race race = makeRace(round.getId(), ecId, "A", 1);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        makeRaceEntry(race.getId(), entry.getId());
        makeSnapshot(race.getId(), String.format(
                "[{\"position\":%d,\"entryId\":%d,\"driverName\":\"X\",\"carNumber\":\"1\","
                + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0}]",
                position, entry.getId()));
    }

    @Test
    void bestXCountsAtMostXRoundsEvenWithFewerThanYRaced() throws Exception {
        // Best 2 from 4, but only three rounds raced: the worst of the three is still dropped
        Championship champ = makeChampionship(0, 0, 2, 4, ScoringSource.FINALS);
        User driver = makeUser("Fay", "Few");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6));
        finishRound(champ, 1, driver, 1);
        finishRound(champ, 2, driver, 2);
        finishRound(champ, 3, driver, 3);

        StandingsRowDto row = query.computeStandings(champ.getId()).get(0);
        assertThat(row.totalPoints()).isEqualTo(18);
        assertThat(row.rounds().stream().filter(RoundResultDto::dropped).count()).isEqualTo(1);
    }

    @Test
    void onlyTheClassesAddedToAChampionshipScore() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        User driver = makeUser("Cleo", "Class");
        addPointsScale(champ.getId(), Map.of(1, 10));
        finishRound(champ, 1, driver, 1);

        // No classes added yet: every class with results scores
        assertThat(query.computeStandings(champ.getId())).hasSize(1);

        ChampionshipClass other = new ChampionshipClass();
        other.setChampionshipId(champ.getId());
        RacingClass otherClass = new RacingClass();
        otherClass.setName("Other-" + UUID.randomUUID());
        otherClass.setCreatedAt(Instant.now());
        otherClass.setUpdatedAt(Instant.now());
        other.setRacingClassId(racingClassRepository.save(otherClass).getId());
        other.setCreatedAt(Instant.now());
        championshipClassRepository.save(other);

        assertThat(query.computeStandings(champ.getId())).isEmpty();
    }

    @Test
    void driversLevelOnPointsAreSeparatedByCountbackAndShareAPositionIfStillLevel() throws Exception {
        // Each driver races two events of their own: Ann 2nd and 2nd (16), Bea 1st and 5th (12), Cat and Dee 3rd
        // and 3rd (12). Bea's win puts her ahead of Cat and Dee on countback, who are level and share third.
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6, 4, 4, 5, 2));
        User ann = makeUser("Ann", "Aa");
        User bea = makeUser("Bea", "Bb");
        User cat = makeUser("Cat", "Cc");
        User dee = makeUser("Dee", "Dd");
        finishRound(champ, 1, ann, 2);
        finishRound(champ, 2, ann, 2);
        finishRound(champ, 3, bea, 1);
        finishRound(champ, 4, bea, 5);
        finishRound(champ, 5, cat, 3);
        finishRound(champ, 6, cat, 3);
        finishRound(champ, 7, dee, 3);
        finishRound(champ, 8, dee, 3);

        List<StandingsRowDto> rows = query.computeStandings(champ.getId());
        assertThat(rows).extracting(StandingsRowDto::totalPoints).containsExactly(16, 12, 12, 12);
        assertThat(rows).extracting(StandingsRowDto::position).containsExactly(1, 2, 3, 3);
    }

    @Test
    void anOverallTableRanksEveryClassTogetherOnChampionshipPoints() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        champ.setShowOverallTable(true);
        championshipRepository.save(champ);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));
        RacingClass fourWd = new RacingClass();
        fourWd.setName("4WD-" + UUID.randomUUID());
        fourWd.setCreatedAt(Instant.now());
        fourWd.setUpdatedAt(Instant.now());
        Long fourWdId = racingClassRepository.save(fourWd).getId();

        Event event = makeEvent("Overall-table-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long twoWdEc = makeEventClass(event.getId());
        Long fourWdEc = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, event.getId())
                .set(EVENT_CLASSES.RACING_CLASS_ID, fourWdId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race twoWdFinal = makeRace(round.getId(), twoWdEc, "A", 1);
        Race fourWdFinal = makeRace(round.getId(), fourWdEc, "A", 2);
        Entry a = makeEntry(makeUser("Two", "Wd").getId(), event.getId(), twoWdEc);
        Entry b = makeEntry(makeUser("Four", "Wd").getId(), event.getId(), fourWdEc);
        makeRaceEntry(twoWdFinal.getId(), a.getId());
        makeRaceEntry(fourWdFinal.getId(), b.getId());
        makeSnapshot(twoWdFinal.getId(), "[" + resultRow(a.getId(), 1, 10, 5000) + "]");
        makeSnapshot(fourWdFinal.getId(), "[" + resultRow(b.getId(), 1, 10, 5000) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // One row in each class and one in the overall table (class id 0, listed first), each driver on 10
        assertThat(standings).extracting(StandingsRowDto::racingClassId)
                .containsExactlyInAnyOrder(racingClassId, fourWdId, ChampionshipStandingsQuery.OVERALL_CLASS_ID,
                        ChampionshipStandingsQuery.OVERALL_CLASS_ID);
        assertThat(standings.get(0).racingClassId()).isEqualTo(ChampionshipStandingsQuery.OVERALL_CLASS_ID);
        assertThat(standings).extracting(StandingsRowDto::totalPoints).containsOnly(10);
        // Level on points and wins, so both share first place in the overall table
        assertThat(standings.stream().filter(r -> r.racingClassId() == ChampionshipStandingsQuery.OVERALL_CLASS_ID)
                .map(StandingsRowDto::position)).containsExactly(1, 1);
    }

    @Test
    void seedingUsesTheStandingsOfTheEarlierRoundsAndNothingForTheFirstRound() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));
        User leader = makeUser("Seed", "Leader");
        finishRound(champ, 1, leader, 1);
        Event next = makeEvent("Seeded-event");
        linkEventToChampionship(champ.getId(), next.getId(), 2);

        assertThat(seedingQuery.positionsFor(next.getId(), racingClassId))
                .containsExactly(Map.entry(competitorIdFor(leader.getId()), 1));
        // A class the championship does not score, and a championship's first round, have nothing to seed from
        assertThat(seedingQuery.positionsFor(next.getId(), -1L)).isNotNull();
        Event first = makeEvent("First-round-event");
        Championship other = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        linkEventToChampionship(other.getId(), first.getId(), 1);
        assertThat(seedingQuery.positionsFor(first.getId(), racingClassId)).isEmpty();
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

    /** One result row, as the snapshot stores it. */
    private static String resultRow(Long entryId, int position, int laps, long bestLapMs) {
        return String.format(
                "{\"position\":%d,\"entryId\":%d,\"driverName\":\"X\",\"carNumber\":\"%d\","
                + "\"lapsCompleted\":%d,\"totalTimeMs\":60000,\"bestLapMs\":%d,\"gapToLeaderMs\":0}",
                position, entryId, position, laps, bestLapMs);
    }

    @Test
    void onlyTheTopQualifierOfTheClassGetsTheTqBonusWhenTwoDriversWinOneHeatEach() throws Exception {
        // CHAMP-07: one TQ per class and round, not one per heat winner (#248)
        Championship champ = makeChampionship(2, 0, null, null, ScoringSource.QUALIFYING);
        User first = makeUser("Fast", "Driver");
        User second = makeUser("Other", "Driver");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Two-winners-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round qualRound = makeRound(event.getId(), RoundType.QUALIFIER, 1);
        Entry a = makeEntry(first.getId(), event.getId(), ecId);
        Entry b = makeEntry(second.getId(), event.getId(), ecId);
        Race heat1 = makeRace(qualRound.getId(), ecId, null, 1);
        Race heat2 = makeRace(qualRound.getId(), ecId, null, 2);
        for (Race heat : List.of(heat1, heat2)) {
            makeRaceEntry(heat.getId(), a.getId());
            makeRaceEntry(heat.getId(), b.getId());
        }
        // Both have 23 laps over the two heats; A has the quicker single lap
        makeSnapshot(heat1.getId(),
                "[" + resultRow(a.getId(), 1, 12, 5000) + "," + resultRow(b.getId(), 2, 11, 5400) + "]");
        makeSnapshot(heat2.getId(),
                "[" + resultRow(b.getId(), 1, 12, 5200) + "," + resultRow(a.getId(), 2, 11, 5600) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // A tops the class overall (23 laps, quicker lap) for 10 and the 2 point bonus; B is second overall for 8,
        // not first of its own heat
        assertThat(standings).extracting(StandingsRowDto::totalPoints).containsExactlyInAnyOrder(12, 8);
    }

    @Test
    void aClassWithFinalsScoresFromItsFinalsNotItsQualifying() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        User driver = makeUser("Finals", "Driver");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Finals-over-qualifying-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round qualRound = makeRound(event.getId(), RoundType.QUALIFIER, 1);
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 2);
        Entry entry = makeEntry(driver.getId(), event.getId(), ecId);
        Race qual = makeRace(qualRound.getId(), ecId, null, 1);
        Race fin = makeRace(finalRound.getId(), ecId, "A", 1);
        makeRaceEntry(qual.getId(), entry.getId());
        makeRaceEntry(fin.getId(), entry.getId());
        makeSnapshot(qual.getId(), "[" + resultRow(entry.getId(), 2, 10, 6000) + "]");
        makeSnapshot(fin.getId(), "[" + resultRow(entry.getId(), 1, 10, 6000) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).hasSize(1);
        // 10 for winning the final; the second place in qualifying scores nothing once the class has finals
        assertThat(standings.get(0).totalPoints()).isEqualTo(10);
        assertThat(standings.get(0).rounds().get(0).position()).isEqualTo(1);
    }

    @Test
    void twoQualifyingHeatsArePlacedAcrossTheWholeClassWhenThereAreNoFinals() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        User first = makeUser("Heat", "One");
        User second = makeUser("Heat", "Two");
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Whole-class-qualifying-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round qualRound = makeRound(event.getId(), RoundType.QUALIFIER, 1);
        Entry a = makeEntry(first.getId(), event.getId(), ecId);
        Entry b = makeEntry(second.getId(), event.getId(), ecId);
        Race heat1 = makeRace(qualRound.getId(), ecId, null, 1);
        Race heat2 = makeRace(qualRound.getId(), ecId, null, 2);
        makeRaceEntry(heat1.getId(), a.getId());
        makeRaceEntry(heat2.getId(), b.getId());
        // Each wins their own heat, but A completed more laps
        makeSnapshot(heat1.getId(), "[" + resultRow(a.getId(), 1, 12, 5000) + "]");
        makeSnapshot(heat2.getId(), "[" + resultRow(b.getId(), 1, 10, 5000) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).extracting(StandingsRowDto::totalPoints).containsExactlyInAnyOrder(10, 8);
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

    /**
     * A competitor with two entries in one class at one event, both on the grid, who starts neither has
     * one DNS round, not two. Only one live entry per class is allowed, so the second was withdrawn after
     * the grid was set.
     */
    @Test
    void twoEntriesInOneClassThatDoNotStartGiveOneDnsRound() {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));

        Event event = makeEvent("Two-Entry-DNS-Event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round round = makeRound(event.getId(), RoundType.FINAL, 1);
        Race race = makeRace(round.getId(), ecId, "A", 1);

        Long competitorId = competitorService.createWalkIn("Twice Entered").getId();
        Entry first = makeCompetitorEntry(competitorId, event.getId(), ecId);
        first.setStatus(EntryStatus.WITHDRAWN);
        first.setWithdrawnAt(Instant.now());
        entryRepository.save(first);
        Entry second = makeCompetitorEntry(competitorId, event.getId(), ecId);
        makeRaceEntry(race.getId(), first.getId());
        makeRaceEntry(race.getId(), second.getId());

        User winner = makeUser("Race", "Winner");
        Entry winnerEntry = makeEntry(winner.getId(), event.getId(), ecId);
        makeRaceEntry(race.getId(), winnerEntry.getId());
        makeSnapshot(race.getId(), String.format(
                "[{\"position\":1,\"entryId\":%d,\"driverName\":\"Race\",\"carNumber\":\"1\","
                + "\"lapsCompleted\":11,\"totalTimeMs\":65000,\"bestLapMs\":5900,\"gapToLeaderMs\":0}]",
                winnerEntry.getId()));

        StandingsRowDto row = query.computeStandings(champ.getId()).stream()
                .filter(r -> r.driverId().equals(competitorId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Twice-entered competitor not found in standings"));

        assertThat(row.rounds()).hasSize(1);
        assertThat(row.rounds().getFirst().position()).isZero();
        assertThat(row.rounds().getFirst().points()).isZero();
        assertThat(row.totalPoints()).isZero();
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

    @Test
    void aBFinalIsPlacedAfterEveryoneInTheAFinal() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6, 4, 4));
        Event event = makeEvent("Stacked-finals-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        Race bFinal = makeRace(finalRound.getId(), ecId, "B", 2);
        Entry ann = makeEntry(makeUser("Ann", "Stack").getId(), event.getId(), ecId);
        Entry bob = makeEntry(makeUser("Bob", "Stack").getId(), event.getId(), ecId);
        Entry cat = makeEntry(makeUser("Cat", "Stack").getId(), event.getId(), ecId);
        Entry dan = makeEntry(makeUser("Dan", "Stack").getId(), event.getId(), ecId);
        makeRaceEntry(aFinal.getId(), ann.getId());
        makeRaceEntry(aFinal.getId(), bob.getId());
        makeRaceEntry(bFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), dan.getId());
        makeSnapshot(aFinal.getId(),
                "[" + resultRow(ann.getId(), 1, 10, 5000) + "," + resultRow(bob.getId(), 2, 10, 5100) + "]");
        makeSnapshot(bFinal.getId(),
                "[" + resultRow(cat.getId(), 1, 9, 5200) + "," + resultRow(dan.getId(), 2, 9, 5300) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // The B final's winner is 3rd overall, not 1st
        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Ann Stack", 10),
                        org.assertj.core.groups.Tuple.tuple("Bob Stack", 8),
                        org.assertj.core.groups.Tuple.tuple("Cat Stack", 6),
                        org.assertj.core.groups.Tuple.tuple("Dan Stack", 4));
    }

    @Test
    void aDriverBumpedUpIsPlacedInTheAFinalOnly() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6, 4, 4));
        Event event = makeEvent("Bumped-finals-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        Race bFinal = makeRace(finalRound.getId(), ecId, "B", 2);
        Entry ann = makeEntry(makeUser("Ann", "Bump").getId(), event.getId(), ecId);
        Entry bob = makeEntry(makeUser("Bob", "Bump").getId(), event.getId(), ecId);
        Entry cat = makeEntry(makeUser("Cat", "Bump").getId(), event.getId(), ecId);
        Entry dan = makeEntry(makeUser("Dan", "Bump").getId(), event.getId(), ecId);
        makeRaceEntry(aFinal.getId(), ann.getId());
        makeRaceEntry(aFinal.getId(), bob.getId());
        makeRaceEntry(aFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), dan.getId());
        makeSnapshot(aFinal.getId(), "[" + resultRow(ann.getId(), 1, 10, 5000) + ","
                + resultRow(bob.getId(), 2, 10, 5100) + "," + resultRow(cat.getId(), 3, 9, 5200) + "]");
        // Cat won the B final to bump up, then came 3rd in the A
        makeSnapshot(bFinal.getId(),
                "[" + resultRow(cat.getId(), 1, 9, 5200) + "," + resultRow(dan.getId(), 2, 9, 5300) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Ann Bump", 10),
                        org.assertj.core.groups.Tuple.tuple("Bob Bump", 8),
                        org.assertj.core.groups.Tuple.tuple("Cat Bump", 6),
                        org.assertj.core.groups.Tuple.tuple("Dan Bump", 4));
    }

    @Test
    void theTqOfALowPointsClassIsTheTopOfTheQualifyingTableNotTheMostLaps() throws Exception {
        Championship champ = makeChampionship(2, 0, null, null, ScoringSource.QUALIFYING);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6));
        Event event = makeEvent("Low-points-tq-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, event.getId())
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"BUMP_UP\",\"qualifyingHeats\":3,"
                        + "\"heatDurationMinutes\":5,\"bestHeatsCount\":2,\"gridSize\":10,\"bumpSpots\":2,"
                        + "\"qualifyingStartType\":\"GRID\",\"finalsStartType\":\"GRID\","
                        + "\"qualifyingType\":\"ROUND_BY_ROUND\",\"racePaddingMinutes\":0,"
                        + "\"staggerIntervalSeconds\":0}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Entry fast = makeEntry(makeUser("Fast", "Laps").getId(), event.getId(), ecId);
        Entry steady = makeEntry(makeUser("Steady", "Points").getId(), event.getId(), ecId);
        Entry gap = makeEntry(makeUser("Gap", "Filler").getId(), event.getId(), ecId);
        // Fast wins round 1 by a mile but is last in rounds 2 and 3, so has the most laps and 4 points from the
        // best 2; Steady is 2nd, then wins twice, for 2 points.
        String[][] rounds = {
                {resultRow(fast.getId(), 1, 100, 5000), resultRow(steady.getId(), 2, 50, 5100),
                        resultRow(gap.getId(), 3, 40, 5200)},
                {resultRow(steady.getId(), 1, 30, 5100), resultRow(gap.getId(), 2, 20, 5200),
                        resultRow(fast.getId(), 3, 10, 5300)},
                {resultRow(steady.getId(), 1, 30, 5100), resultRow(gap.getId(), 2, 20, 5200),
                        resultRow(fast.getId(), 3, 10, 5300)}};
        for (int i = 0; i < rounds.length; i++) {
            Round round = makeRound(event.getId(), RoundType.QUALIFIER, i + 1);
            Race heat = makeRace(round.getId(), ecId, null, 1);
            makeRaceEntry(heat.getId(), fast.getId());
            makeRaceEntry(heat.getId(), steady.getId());
            makeRaceEntry(heat.getId(), gap.getId());
            makeSnapshot(heat.getId(), "[" + String.join(",", rounds[i]) + "]");
        }

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // Placed by the round by round qualifying table: Steady first (2 points), Fast and Gap level on 4 with
        // Fast ahead on his best round, so 10, 8 and 6, and only Steady has the 2 point TQ bonus
        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Fast Laps", 8),
                        org.assertj.core.groups.Tuple.tuple("Steady Points", 12),
                        org.assertj.core.groups.Tuple.tuple("Gap Filler", 6));
    }

    @Test
    void anAFinalRunInLegsIsPlacedFromItsCombinedBestTwoOfThreeLegs() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6));
        Event event = makeEvent("A-final-legs-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, event.getId())
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"POINTS_FINALS\",\"qualifyingHeats\":2,"
                        + "\"finalsCount\":1,\"finalDurationMinutes\":5,\"heatDurationMinutes\":5,"
                        + "\"qualifyingStartType\":\"GRID\",\"finalsStartType\":\"GRID\","
                        + "\"qualifyingType\":\"ROUND_BY_ROUND\",\"racePaddingMinutes\":0,"
                        + "\"staggerIntervalSeconds\":0,\"finalLegs\":3,\"legsCounted\":2}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Entry a = makeEntry(makeUser("Ann", "Leg").getId(), event.getId(), ecId);
        Entry b = makeEntry(makeUser("Bob", "Leg").getId(), event.getId(), ecId);
        Entry c = makeEntry(makeUser("Cat", "Leg").getId(), event.getId(), ecId);
        // Ann wins leg 1 only, Bob wins legs 2 and 3: best two legs are Ann 1 + 2, Bob 1 + 1, Cat 2 + 3
        String[][] legs = {
                {resultRow(a.getId(), 1, 12, 5000), resultRow(b.getId(), 2, 10, 5000), resultRow(c.getId(), 3, 8, 5000)},
                {resultRow(b.getId(), 1, 12, 5000), resultRow(a.getId(), 2, 10, 5000), resultRow(c.getId(), 3, 8, 5000)},
                {resultRow(b.getId(), 1, 11, 5000), resultRow(c.getId(), 2, 10, 5000), resultRow(a.getId(), 3, 5, 5000)}};
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        for (int leg = 0; leg < legs.length; leg++) {
            Race race = makeRace(finalRound.getId(), ecId, "A", leg + 1);
            makeRaceEntry(race.getId(), a.getId());
            makeRaceEntry(race.getId(), b.getId());
            makeRaceEntry(race.getId(), c.getId());
            makeSnapshot(race.getId(), "[" + String.join(",", legs[leg]) + "]");
        }

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // Placed Bob, Ann, Cat; without combining, Ann's win in leg 1 would have scored first place
        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Bob Leg", 10),
                        org.assertj.core.groups.Tuple.tuple("Ann Leg", 8),
                        org.assertj.core.groups.Tuple.tuple("Cat Leg", 6));
    }

    @Test
    void aBFinalIsNotPlacedWhileTheAFinalIsStillToRun() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));
        Event event = makeEvent("Open-a-final-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        aFinal.setStatus(RaceStatus.PENDING);
        raceRepository.save(aFinal);
        Race bFinal = makeRace(finalRound.getId(), ecId, "B", 2);
        Entry cat = makeEntry(makeUser("Cat", "Open").getId(), event.getId(), ecId);
        makeRaceEntry(bFinal.getId(), cat.getId());
        makeSnapshot(bFinal.getId(), "[" + resultRow(cat.getId(), 1, 9, 5200) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // The B winner may still bump up, so the B final scores nothing until the A final has finished
        assertThat(standings).extracting(StandingsRowDto::totalPoints).containsOnly(0);
    }

    @Test
    void aDriverBumpedUpWhoDoesNotStartTheAFinalIsClassedLastInIt() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8, 3, 6, 4, 4));
        Event event = makeEvent("Bumped-no-start-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        Race bFinal = makeRace(finalRound.getId(), ecId, "B", 2);
        Entry ann = makeEntry(makeUser("Ann", "NoStart").getId(), event.getId(), ecId);
        Entry bob = makeEntry(makeUser("Bob", "NoStart").getId(), event.getId(), ecId);
        Entry cat = makeEntry(makeUser("Cat", "NoStart").getId(), event.getId(), ecId);
        Entry dan = makeEntry(makeUser("Dan", "NoStart").getId(), event.getId(), ecId);
        makeRaceEntry(aFinal.getId(), ann.getId());
        makeRaceEntry(aFinal.getId(), bob.getId());
        makeRaceEntry(aFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), dan.getId());
        makeSnapshot(aFinal.getId(),
                "[" + resultRow(ann.getId(), 1, 10, 5000) + "," + resultRow(bob.getId(), 2, 10, 5100) + "]");
        makeSnapshot(bFinal.getId(),
                "[" + resultRow(cat.getId(), 1, 9, 5200) + "," + resultRow(dan.getId(), 2, 9, 5300) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // Cat did not start the A final: last in it (3rd), ahead of Dan from the B final
        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Ann NoStart", 10),
                        org.assertj.core.groups.Tuple.tuple("Bob NoStart", 8),
                        org.assertj.core.groups.Tuple.tuple("Cat NoStart", 6),
                        org.assertj.core.groups.Tuple.tuple("Dan NoStart", 4));
    }

    @Test
    void aHigherFinalNobodyRacedTakesNoOneFromTheFinalBelow() throws Exception {
        Championship champ = makeChampionship(0, 0, null, null, ScoringSource.FINALS);
        addPointsScale(champ.getId(), Map.of(1, 10, 2, 8));
        Event event = makeEvent("Empty-a-final-event");
        linkEventToChampionship(champ.getId(), event.getId(), 1);
        Long ecId = makeEventClass(event.getId());
        Round finalRound = makeRound(event.getId(), RoundType.FINAL, 1);
        Race aFinal = makeRace(finalRound.getId(), ecId, "A", 1);
        Race bFinal = makeRace(finalRound.getId(), ecId, "B", 2);
        Entry cat = makeEntry(makeUser("Cat", "Empty").getId(), event.getId(), ecId);
        Entry dan = makeEntry(makeUser("Dan", "Empty").getId(), event.getId(), ecId);
        makeRaceEntry(aFinal.getId(), cat.getId());
        makeRaceEntry(aFinal.getId(), dan.getId());
        makeRaceEntry(bFinal.getId(), cat.getId());
        makeRaceEntry(bFinal.getId(), dan.getId());
        makeSnapshot(aFinal.getId(), "[]");
        makeSnapshot(bFinal.getId(),
                "[" + resultRow(cat.getId(), 1, 9, 5200) + "," + resultRow(dan.getId(), 2, 9, 5300) + "]");

        List<StandingsRowDto> standings = query.computeStandings(champ.getId());

        // Nobody raced the A final, so the B final places stand
        assertThat(standings).extracting(StandingsRowDto::displayName, StandingsRowDto::totalPoints)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Cat Empty", 10),
                        org.assertj.core.groups.Tuple.tuple("Dan Empty", 8));
    }
}
