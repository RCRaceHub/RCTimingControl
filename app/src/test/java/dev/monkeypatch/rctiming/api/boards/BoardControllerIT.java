package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.domain.event.PracticeRanking;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spectator board read API (L12), called with no Authorization header throughout.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class BoardControllerIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired CompetitorService competitorService;
    @Autowired ResultSnapshotRepository resultSnapshotRepository;
    @Autowired LapTimingService lapTimingService;
    @Autowired JdbcTemplate jdbc;
    @Autowired BoardQuery boardQuery;
    @Autowired EventService eventService;

    private BoardFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new BoardFixtures(eventRepository, racingClassRepository, eventClassRepository,
                roundRepository, raceRepository, entryRepository, raceEntryRepository, competitorService);
    }

    @AfterEach
    void tearDown() {
        fx.cleanUp();
    }

    @Test
    void finalsBoard_listsFinalsInLetterOrderAndLeavesQualifiersOut() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        fx.race(q1, 1, RaceStatus.FINISHED);
        Round finals = fx.round(RoundType.FINAL, 1, 2);
        Race b = fx.race(finals, 1, RaceStatus.PENDING);
        b.setFinalLetter("B");
        fx.save(b);
        Race a = fx.race(finals, 1, RaceStatus.PENDING);
        a.setFinalLetter("A");
        fx.save(a);

        Map body = get("/api/v1/boards/finals?eventId=" + fx.event.getId());

        List<Map> classes = (List<Map>) body.get("classes");
        assertThat(classes).hasSize(1);
        assertThat(((Number) classes.get(0).get("finalsTotal")).intValue()).isEqualTo(2);
        List<Map> shown = (List<Map>) classes.get(0).get("finals");
        assertThat(shown).extracting(f -> f.get("letter")).containsExactly("A", "B");
        assertThat((List) shown.get(0).get("results")).isEmpty();
    }

    @Test
    void nothingFinished_resultsBoardHasNoRaceAndNoRows() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        fx.race(q1, 1, RaceStatus.PENDING);

        Map body = get("/api/v1/boards/results?eventId=" + fx.event.getId());

        assertThat(((Number) body.get("eventId")).longValue()).isEqualTo(fx.event.getId());
        assertThat(body.get("eventName")).isEqualTo(fx.event.getName());
        assertThat(body.get("race")).isNull();
        assertThat((List) body.get("results")).isEmpty();
    }

    @Test
    void beforeTheFirstHeat_nowNextShowsOnlyTheNextRace() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.PENDING);
        fx.race(q1, 2, RaceStatus.PENDING);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        assertThat(body.get("currentRace")).isNull();
        assertThat(body.get("lastCompletedRace")).isNull();
        Map next = (Map) body.get("nextRace");
        assertThat(((Number) next.get("raceId")).longValue()).isEqualTo(heat1.getId());
        assertThat(next.get("label")).isEqualTo("Qualifying 1 — " + fx.className + " — Heat 1");
        assertThat(next.get("status")).isEqualTo("PENDING");
    }

    @Test
    void whileRacing_nowNextShowsCurrentNextAndLastFinished() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        Race heat2 = fx.race(q1, 2, RaceStatus.RUNNING);
        Race heat3 = fx.race(q1, 3, RaceStatus.GRID);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        assertThat(raceId(body, "currentRace")).isEqualTo(heat2.getId());
        assertThat(raceId(body, "nextRace")).isEqualTo(heat3.getId());
        assertThat(raceId(body, "lastCompletedRace")).isEqualTo(heat1.getId());
    }

    @Test
    void stoppedRace_isStillTheCurrentRace() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.STOPPED);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        Map current = (Map) body.get("currentRace");
        assertThat(((Number) current.get("raceId")).longValue()).isEqualTo(heat1.getId());
        assertThat(current.get("status")).isEqualTo("STOPPED");
    }

    @Test
    void betweenRounds_nextRaceFollowsRunOrderAcrossRounds() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Round q2 = fx.round(RoundType.QUALIFIER, 2, 2);
        fx.race(q1, 1, RaceStatus.FINISHED);
        Race lastOfQ1 = fx.race(q1, 2, RaceStatus.FINISHED);
        lastOfQ1.setFinishedAt(Instant.now().plus(1, ChronoUnit.MINUTES));
        fx.save(lastOfQ1);
        // Created first but later in run order, so the board must not pick it by id
        Race q2heat2 = fx.race(q2, 2, RaceStatus.PENDING);
        Race q2heat1 = fx.race(q2, 1, RaceStatus.PENDING);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        assertThat(body.get("currentRace")).isNull();
        assertThat(raceId(body, "nextRace")).isEqualTo(q2heat1.getId());
        assertThat(raceId(body, "nextRace")).isNotEqualTo(q2heat2.getId());
        assertThat(raceId(body, "lastCompletedRace")).isEqualTo(lastOfQ1.getId());
    }

    @Test
    void skippedAhead_theRaceOnTheGridIsNextNotTheEarlierPendingOne() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        fx.race(q1, 1, RaceStatus.PENDING);
        fx.race(q1, 2, RaceStatus.PENDING);
        // CTRL-09: the director skipped to heat 3 and called its grid
        Race heat3 = fx.race(q1, 3, RaceStatus.GRID);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        assertThat(raceId(body, "nextRace")).isEqualTo(heat3.getId());
    }

    @Test
    void afterTheLastRace_showsOnlyTheLastFinishedRace() {
        Round fin = fx.round(RoundType.FINAL, 1, 1);
        Race aFinal = fx.race(fin, 1, RaceStatus.FINISHED);
        aFinal.setFinalLetter("A");
        fx.save(aFinal);

        Map body = get("/api/v1/boards/now-next?eventId=" + fx.event.getId());

        assertThat(body.get("currentRace")).isNull();
        assertThat(body.get("nextRace")).isNull();
        Map last = (Map) body.get("lastCompletedRace");
        assertThat(last.get("label")).isEqualTo("A Final — " + fx.className);
    }

    @Test
    void highlights_answerForAHeatWithNothingStoredYetAndAFinishedOneInItsRound() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        Race heat2 = fx.race(q1, 2, RaceStatus.RUNNING);
        saveSnapshot(heat1, """
                [{"position":1,"entryId":11,"competitorId":null,"driverName":"Jane Doe","carNumber":"7",
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null}]
                """);

        Map body = get("/api/v1/boards/races/" + heat2.getId() + "/highlights");

        Map heat = (Map) body.get("heat");
        assertThat(heat.get("gained")).isNull();
        assertThat(heat.get("avg3")).isNull();
        assertThat(body.get("round")).isNotNull();
    }

    @Test
    void roundFastestLap_isTheBestLapOfTheOtherFinishedHeatsInTheRound() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        Race heat2 = fx.race(q1, 2, RaceStatus.RUNNING);
        saveSnapshot(heat1, """
                [{"position":1,"entryId":11,"competitorId":null,"driverName":"Jane Doe","carNumber":"7",
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":12,"competitorId":null,"driverName":"Sam Second","carNumber":null,
                  "lapsCompleted":11,"totalTimeMs":301000,"bestLapMs":18500,"gapToLeaderMs":1000}]
                """);

        Map body = get("/api/v1/boards/races/" + heat2.getId() + "/round-fastest-lap");
        assertThat(((Number) body.get("lapMs")).longValue()).isEqualTo(18500);
        assertThat(body.get("driverName")).isEqualTo("Sam Second");

        Map none = get("/api/v1/boards/races/" + heat1.getId() + "/round-fastest-lap");
        assertThat(none.get("lapMs")).isNull();
    }

    @Test
    void resultsBoard_showsTheLastFinishedRacesSnapshot() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        saveSnapshot(heat1, """
                [{"position":1,"entryId":11,"competitorId":null,"driverName":"Jane Doe","carNumber":"7",
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":12,"competitorId":null,"driverName":"Sam Second","carNumber":null,
                  "lapsCompleted":11,"totalTimeMs":301000,"bestLapMs":19500,"gapToLeaderMs":1000}]
                """);

        Map body = get("/api/v1/boards/results?eventId=" + fx.event.getId());

        assertThat(raceId(body, "race")).isEqualTo(heat1.getId());
        List<Map> rows = (List<Map>) body.get("results");
        assertThat(rows).extracting(r -> r.get("driverName")).containsExactly("Jane Doe", "Sam Second");
    }

    @Test
    void qualifyingBoard_ranksTheClassAndShowsEachNameAsTheCompetitorAllows() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        Entry slow = fx.gridEntry(heat1, "Ada Lovelace", "T-2", 2);
        Entry fast = fx.gridEntry(heat1, "Fast Freddie", "T-1", 1);
        jdbc.update("update competitors set name_visibility = 'INITIALS' where id = ?", slow.getCompetitorId());
        saveSnapshot(heat1, String.format("""
                [{"position":1,"entryId":%d,"competitorId":null,"driverName":"Fast Freddie","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":%d,"competitorId":null,"driverName":"Ada Lovelace","carNumber":null,
                  "lapsCompleted":10,"totalTimeMs":301000,"bestLapMs":19500,"gapToLeaderMs":1000}]
                """, fast.getId(), slow.getId()));

        Map body = get("/api/v1/boards/qualifying?eventId=" + fx.event.getId());

        assertThat(body.get("eventName")).isEqualTo(fx.event.getName());
        List<Map> tables = (List<Map>) body.get("tables");
        assertThat(tables).singleElement().satisfies(table -> {
            assertThat(table.get("className")).isEqualTo(fx.className);
            List<Map> rows = (List<Map>) table.get("rows");
            assertThat(rows).extracting(r -> r.get("driverName")).containsExactly("Fast Freddie", "A. L.");
            assertThat(rows).extracting(r -> r.get("lapsCompleted")).containsExactly(12, 10);
        });
    }

    @Test
    void currentRace_carriesTheBumpUpPlacesOfAFinalBelowTheOneThatTakesThem() {
        Round finals = fx.round(RoundType.FINAL, 1, 2);
        Race a = fx.race(finals, 1, RaceStatus.PENDING);
        a.setFinalLetter("A");
        a.setBumpSlots(3);
        fx.save(a);
        Race zombie = fx.race(finals, 2, RaceStatus.PENDING);
        zombie.setFinalLetter("Z");
        fx.save(zombie);
        Race b = fx.race(finals, 3, RaceStatus.RUNNING);
        b.setFinalLetter("B");
        fx.save(b);

        // A's third slot is kept for the zombie round's winner while that round is still to run
        assertThat(boardQuery.currentRace(fx.event.getId())).get()
                .extracting(r -> r.bumpUpPlaces()).isEqualTo(2);

        zombie.setStatus(RaceStatus.FINISHED);
        fx.save(zombie);
        assertThat(boardQuery.currentRace(fx.event.getId())).get()
                .extracting(r -> r.bumpUpPlaces()).isEqualTo(3);

        a.setStatus(RaceStatus.RUNNING);
        b.setStatus(RaceStatus.PENDING);
        fx.save(a);
        fx.save(b);
        assertThat(boardQuery.currentRace(fx.event.getId())).get()
                .extracting(r -> r.bumpUpPlaces()).isEqualTo(0);
    }

    @Test
    void lastCompletedQualifier_isStillTheQualifyingHeatOnceAFinalHasFinished() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);
        Round finals = fx.round(RoundType.FINAL, 1, 2);
        Race finalA = fx.race(finals, 1, RaceStatus.FINISHED);
        finalA.setFinalLetter("A");
        fx.save(finalA);

        assertThat(boardQuery.lastCompletedRace(fx.event.getId())).get()
                .extracting(r -> r.raceId()).isEqualTo(finalA.getId());
        assertThat(boardQuery.lastCompletedQualifier(fx.event.getId())).get()
                .extracting(r -> r.raceId()).isEqualTo(heat1.getId());
    }

    @Test
    void liveTop_beforeQualifyingShowsPracticeRankedByTheEventsSetting() {
        Race session1 = fx.race(fx.round(RoundType.PRACTICE, 1, 1), 1, RaceStatus.FINISHED);
        Entry ada = fx.gridEntry(session1, "Ada Lovelace", "T-1", 1);
        Entry bob = fx.gridEntry(session1, "Bob Builder", "T-2", 2);
        saveLapHistory(session1, lap(1, ada, 15000) + "," + lap(2, ada, 14000) + ","
                + lap(1, bob, 12500) + "," + lap(2, bob, 16000));
        Race session2 = fx.race(fx.round(RoundType.PRACTICE, 2, 2), 1, RaceStatus.FINISHED);
        saveLapHistory(session2, lap(1, ada, 14500) + "," + lap(2, ada, 13000));

        Map fastest = get("/api/v1/boards/live-qualifying?eventId=" + fx.event.getId());

        assertThat(fastest.get("roundType")).isEqualTo("PRACTICE");
        assertThat(fastest.get("phase")).isEqualTo("FINISHED");
        assertThat(((Number) fastest.get("roundNumber")).intValue()).isEqualTo(2);
        assertThat(fastest.get("rankingLabel")).isEqualTo("Fastest lap");
        assertThat((List<Map>) fastest.get("rows")).extracting(r -> r.get("driverName"))
                .containsExactly("Bob Builder", "Ada Lovelace");
        assertThat((List<Map>) fastest.get("roundRows")).extracting(r -> r.get("driverName"))
                .containsExactly("Ada Lovelace");

        eventService.setPracticeRanking(Actor.system("test"), fx.event.getId(), PracticeRanking.BEST_2);
        Map bestTwo = get("/api/v1/boards/live-qualifying?eventId=" + fx.event.getId());

        assertThat(bestTwo.get("rankingLabel")).isEqualTo("Best 2 laps in a row");
        List<Map> rows = (List<Map>) bestTwo.get("rows");
        assertThat(rows).extracting(r -> r.get("driverName")).containsExactly("Ada Lovelace", "Bob Builder");
        assertThat(rows).extracting(r -> ((Number) r.get("bestLapMs")).longValue()).containsExactly(27500L, 28500L);
    }

    @Test
    void liveTop_forAFinalRunInLegsShowsTheLastLegBesideTheCombinedOrder() {
        Round aRound = fx.round(RoundType.FINAL, 1, 1);
        Race leg1 = fx.race(aRound, 1, RaceStatus.FINISHED);
        leg1.setFinalLetter("A");
        fx.save(leg1);
        Entry ada = fx.gridEntry(leg1, "Ada Lovelace", "T-1", 1);
        Entry bob = fx.gridEntry(leg1, "Bob Builder", "T-2", 2);
        saveSnapshot(leg1, String.format("""
                [{"position":1,"entryId":%d,"competitorId":null,"driverName":"Ada Lovelace","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":%d,"competitorId":null,"driverName":"Bob Builder","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":301000,"bestLapMs":19500,"gapToLeaderMs":1000}]
                """, ada.getId(), bob.getId()));
        Race leg2 = fx.race(aRound, 2, RaceStatus.FINISHED);
        leg2.setFinalLetter("A");
        fx.save(leg2);
        saveSnapshot(leg2, String.format("""
                [{"position":1,"entryId":%d,"competitorId":null,"driverName":"Bob Builder","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":299000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":%d,"competitorId":null,"driverName":"Ada Lovelace","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":301000,"bestLapMs":19500,"gapToLeaderMs":2000}]
                """, bob.getId(), ada.getId()));

        Map body = get("/api/v1/boards/live-qualifying?eventId=" + fx.event.getId());

        assertThat(body.get("roundType")).isEqualTo("FINAL");
        assertThat(body.get("phase")).isEqualTo("FINISHED");
        assertThat(body.get("roundTitle")).isEqualTo("Leg 2 of 2");
        assertThat(body.get("overallTitle")).isEqualTo("Combined");
        assertThat((List<Map>) body.get("roundRows")).extracting(r -> r.get("driverName"))
                .containsExactly("Bob Builder", "Ada Lovelace");
        List<Map> rows = (List<Map>) body.get("rows");
        // 3 points each (1 + 2); Bob has the least time over the two legs
        assertThat(rows).extracting(r -> r.get("driverName")).containsExactly("Bob Builder", "Ada Lovelace");
        assertThat(rows).extracting(r -> r.get("points")).containsExactly(3, 3);
    }

    @Test
    void finalsBoard_combinesTheLegsOfAFinalOtherThanAWhenItRunsInLegs() {
        Round bRound = fx.round(RoundType.FINAL, 2, 1);
        Race leg1 = fx.race(bRound, 1, RaceStatus.FINISHED);
        leg1.setFinalLetter("B");
        fx.save(leg1);
        Entry ada = fx.gridEntry(leg1, "Ada Lovelace", "T-1", 1);
        Entry bob = fx.gridEntry(leg1, "Bob Builder", "T-2", 2);
        saveSnapshot(leg1, String.format("""
                [{"position":1,"entryId":%d,"competitorId":null,"driverName":"Ada Lovelace","carNumber":null,
                  "lapsCompleted":12,"totalTimeMs":300000,"bestLapMs":19000,"gapToLeaderMs":null},
                 {"position":2,"entryId":%d,"competitorId":null,"driverName":"Bob Builder","carNumber":null,
                  "lapsCompleted":11,"totalTimeMs":301000,"bestLapMs":19500,"gapToLeaderMs":1000}]
                """, ada.getId(), bob.getId()));
        Race leg2 = fx.race(bRound, 2, RaceStatus.PENDING);
        leg2.setFinalLetter("B");
        fx.save(leg2);

        Map body = get("/api/v1/boards/finals?eventId=" + fx.event.getId());

        List<Map> classes = (List<Map>) body.get("classes");
        List<Map> finals = (List<Map>) classes.get(0).get("finals");
        Map bFinal = finals.get(0);
        assertThat(bFinal.get("letter")).isEqualTo("B");
        // Leg 1 is over but the final is not, so it is still running
        assertThat(bFinal.get("status")).isEqualTo("RUNNING");
        Map legs = (Map) bFinal.get("legs");
        assertThat(legs.get("finalLetter")).isEqualTo("B");
        assertThat(((Number) legs.get("legs")).intValue()).isEqualTo(2);
        assertThat(((Number) legs.get("legsFinished")).intValue()).isEqualTo(1);
    }

    @Test
    void qualifyingBoard_withNoQualifyingHeatsHasNoTables() {
        Map body = get("/api/v1/boards/qualifying?eventId=" + fx.event.getId());

        assertThat((List) body.get("tables")).isEmpty();
    }

    @Test
    void finishedWithoutASnapshot_resultsAreEmptyNotAnError() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.FINISHED);

        Map body = get("/api/v1/boards/results?eventId=" + fx.event.getId());

        assertThat(raceId(body, "race")).isEqualTo(heat1.getId());
        assertThat((List) body.get("results")).isEmpty();
    }

    @Test
    void withNoEventId_theBoardShowsTheEventThatIsRacing() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.RUNNING);
        // Started later than any race another test may have left running
        heat1.setStartedAt(Instant.now().plus(1, ChronoUnit.DAYS));
        fx.save(heat1);

        Map body = get("/api/v1/boards/now-next");

        assertThat(((Number) body.get("eventId")).longValue()).isEqualTo(fx.event.getId());
        assertThat(raceId(body, "currentRace")).isEqualTo(heat1.getId());
    }

    @Test
    void unknownEvent_returnsAnEmptyBoard() {
        Map body = get("/api/v1/boards/now-next?eventId=999999999");

        assertThat(body.get("eventId")).isNull();
        assertThat(body.get("currentRace")).isNull();
        assertThat(body.get("nextRace")).isNull();
        assertThat(body.get("lastCompletedRace")).isNull();
    }

    @Test
    void liveTiming_isEmptyBeforeTheFirstLapThenShowsTheField() throws Exception {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.RUNNING);
        String transponder = "B" + fx.suffix;
        fx.gridEntry(heat1, "Live Racer " + fx.suffix, transponder, 1);
        String url = "/api/v1/boards/races/" + heat1.getId() + "/live-timing";

        try {
            assertThat(getList(url)).isEmpty();

            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), transponder, 1_000_000L));
            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), transponder, 21_000_000L));

            List<Map> rows = awaitRows(url);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("driverName")).isEqualTo("Live Racer " + fx.suffix);
        } finally {
            lapTimingService.releaseState(heat1.getId());
        }
    }

    private static int lapOf(Map place) {
        return ((Number) place.get("lap")).intValue();
    }

    private static List<Map> lapOne(List<Map> places) {
        return places.stream().filter(p -> lapOf(p) == 1).toList();
    }

    @Test
    void positionHistory_givesEachCarsPlaceAtTheEndOfEachLap() throws InterruptedException {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.RUNNING);
        String slow = "S" + fx.suffix;
        String quick = "Q" + fx.suffix;
        Entry slowEntry = fx.gridEntry(heat1, "Slow Racer " + fx.suffix, slow, 1);
        Entry quickEntry = fx.gridEntry(heat1, "Quick Racer " + fx.suffix, quick, 2);
        String url = "/api/v1/boards/races/" + heat1.getId() + "/position-history";

        try {
            assertThat(getList(url)).isEmpty();

            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), slow, 1_000_000L));
            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), quick, 2_000_000L));
            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), quick, 20_000_000L));
            lapTimingService.onLapPassing(new LapPassingEvent(heat1.getId(), slow, 22_000_000L));

            // Passings are handled asynchronously: wait for both cars to have completed the lap
            await().atMost(Duration.ofSeconds(5)).until(() -> lapOne(getList(url)).size() >= 2);
            List<Map> places = getList(url);
            assertThat(lapOne(places)).hasSize(2);
            // Lap 0 is the grid: the slow car set off first
            Map gridFirst = places.stream().filter(p -> lapOf(p) == 0 && ((Number) p.get("position")).intValue() == 1)
                    .findFirst().orElseThrow();
            assertThat(((Number) gridFirst.get("entryId")).longValue()).isEqualTo(slowEntry.getId());
            Map first = lapOne(places).stream().filter(p -> ((Number) p.get("position")).intValue() == 1).findFirst().orElseThrow();
            assertThat(((Number) first.get("lap")).intValue()).isEqualTo(1);
            assertThat(((Number) first.get("entryId")).longValue()).isEqualTo(quickEntry.getId());
            assertThat(lapOne(places)).extracting(p -> ((Number) p.get("entryId")).longValue())
                    .containsExactlyInAnyOrder(quickEntry.getId(), slowEntry.getId());
        } finally {
            lapTimingService.releaseState(heat1.getId());
        }
    }

    @Test
    void ticker_namesTheNextRaceAndItsDrivers() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.PENDING);
        fx.gridEntry(heat1, "Next Racer " + fx.suffix, "T" + fx.suffix, 1);

        Map body = get("/api/v1/boards/ticker?eventId=" + fx.event.getId());

        assertThat(body.get("nextRace")).isEqualTo("Qualifying 1 — " + fx.className + " — Heat 1");
        assertThat((List) body.get("nextDrivers")).containsExactly("Next Racer " + fx.suffix);
        assertThat((List) body.get("marshals")).isEmpty();
        assertThat((List) body.get("topQualifiers")).isEmpty();
    }

    @Test
    void raceClock_givesTheTimeSoFarAndTheLengthFromTheFormat() {
        Round q1 = fx.round(RoundType.QUALIFIER, 1, 1);
        Race heat1 = fx.race(q1, 1, RaceStatus.RUNNING);
        // Started before the app saw it, so the clock is worked out from the start time
        heat1.setStartedAt(Instant.now().minusSeconds(90));
        fx.save(heat1);

        Map body = get("/api/v1/boards/races/" + heat1.getId() + "/clock");

        assertThat(body.get("status")).isEqualTo("RUNNING");
        assertThat(body.get("running")).isEqualTo(true);
        assertThat(((Number) body.get("elapsedMs")).longValue()).isBetween(89_000L, 120_000L);
        assertThat(((Number) body.get("durationMs")).longValue()).isEqualTo(300_000L);
        assertThat(((Number) body.get("remainingMs")).longValue())
                .isEqualTo(300_000L - ((Number) body.get("elapsedMs")).longValue());
    }

    @Test
    void raceClock_unknownRaceIsNotFound() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/v1/boards/races/999999999/clock", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void boardEndpointsAreReadOnly() {
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/boards/now-next", null, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    private Map get(String url) {
        ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<Map> getList(String url) {
        ResponseEntity<List> resp = restTemplate.getForEntity(url, List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    /** Lap passings are handled asynchronously, so poll until the field appears. */
    private List<Map> awaitRows(String url) throws InterruptedException {
        await().atMost(Duration.ofSeconds(5)).until(() -> !getList(url).isEmpty());
        return getList(url);
    }

    private static long raceId(Map body, String key) {
        return ((Number) ((Map) body.get(key)).get("raceId")).longValue();
    }

    private static String lap(int lapNumber, Entry entry, long lapTimeMs) {
        return String.format("{\"lapNumber\":%d,\"entryId\":%d,\"position\":1,\"lapTimeMs\":%d}",
                lapNumber, entry.getId(), lapTimeMs);
    }

    private void saveLapHistory(Race race, String lapsJson) {
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setRaceId(race.getId());
        snapshot.setFinishedAt(race.getFinishedAt() != null ? race.getFinishedAt() : Instant.now());
        snapshot.setPositionsJson("[]");
        snapshot.setLapHistoryJson("[" + lapsJson + "]");
        snapshot.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snapshot);
    }

    private void saveSnapshot(Race race, String positionsJson) {
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setRaceId(race.getId());
        snapshot.setFinishedAt(race.getFinishedAt());
        snapshot.setPositionsJson(positionsJson);
        snapshot.setLapHistoryJson("[]");
        snapshot.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snapshot);
    }
}
