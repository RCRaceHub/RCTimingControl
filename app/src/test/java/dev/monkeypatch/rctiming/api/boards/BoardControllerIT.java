package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
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
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

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
        long deadline = System.currentTimeMillis() + 5_000;
        List<Map> rows = getList(url);
        while (rows.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            rows = getList(url);
        }
        return rows;
    }

    private static long raceId(Map body, String key) {
        return ((Number) ((Map) body.get(key)).get("raceId")).longValue();
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
