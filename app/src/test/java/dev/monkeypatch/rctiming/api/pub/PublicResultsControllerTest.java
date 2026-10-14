package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PublicResultsController.
 * Tests: public GET /api/v1/results/{raceId} returns snapshot without auth (RESULT-01);
 *        unknown raceId returns 404 not 403.
 */
class PublicResultsControllerTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    ResultSnapshotRepository resultSnapshotRepository;

    @Autowired
    DSLContext dsl;

    private Long finishedRaceId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        // event
        Event event = new Event();
        event.setName("Test Event " + UUID.randomUUID());
        event.setEventDate(LocalDate.of(2026, 6, 1));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event = eventRepository.save(event);

        // racing class + event_class, inserted directly with jOOQ
        RacingClass rc = new RacingClass();
        rc.setName("GT12-" + UUID.randomUUID());
        rc.setCreatedAt(now);
        rc.setUpdatedAt(now);
        Long racingClassId = racingClassRepository.save(rc).getId();

        Long eventId = event.getId();
        Long eventClassId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID)
                .fetchOne()
                .get(EVENT_CLASSES.ID));

        // round
        Round round = new Round();
        round.setEventId(event.getId());
        round.setType(RoundType.FINAL);
        round.setRoundNumber(1);
        round.setSequenceInEvent(1);
        round.setCreatedAt(now);
        round.setUpdatedAt(now);
        round = roundRepository.save(round);

        // race
        Race race = new Race();
        race.setRoundId(round.getId());
        race.setEventClassId(eventClassId);
        race.setHeatNumber(1);
        race.setSequenceInRound(1);
        race.setFinalLetter("A");
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.FINISHED);
        race.setCreatedAt(now);
        race.setUpdatedAt(now);
        race.setFinishedAt(now);
        race = raceRepository.save(race);
        finishedRaceId = race.getId();

        // result snapshot
        ResultSnapshot snap = new ResultSnapshot();
        snap.setRaceId(finishedRaceId);
        snap.setPositionsJson("[]");
        snap.setLapHistoryJson("[]");
        snap.setFinishedAt(now);
        snap.setCreatedAt(now);
        resultSnapshotRepository.save(snap);
    }

    @Test
    void publicResultsReturnSnapshotWithoutAuth() {
        // No Authorization header — TestRestTemplate sends no auth by default
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/results/" + finishedRaceId, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("raceId");
    }

    @Test
    void unknownRaceIdReturns404NotForbidden() {
        // Must be 404 — public endpoint; unknown ID must not return 403 (which implies auth gate)
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/results/999999", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
