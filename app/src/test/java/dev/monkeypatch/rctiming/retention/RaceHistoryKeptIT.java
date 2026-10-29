package dev.monkeypatch.rctiming.retention;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RCTC is the permanent record of everything it timed (#472): the nightly clean-up must never remove a race, a lap,
 * a result or a practice session. If someone adds a table of race history, list it in {@link #HISTORY_TABLES}.
 */
class RaceHistoryKeptIT extends AbstractIntegrationTest {

    /** Tables that hold what happened on the track; laps live inside {@code result_snapshots} and {@code practice_laps}. */
    static final List<String> HISTORY_TABLES = List.of("events", "rounds", "races", "race_entries", "result_snapshots",
            "practice_sessions", "practice_drivers", "practice_laps", "marshal_adjustments", "penalties",
            "race_point_penalties", "championships");

    @Autowired RetentionService retention;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventRepository eventRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired ResultSnapshotRepository resultSnapshotRepository;
    @Autowired DSLContext dsl;

    /** One event with a round, a race and its result, so the counts below are not all zero. */
    private Runnable seedHistory() {
        Event event = new Event();
        event.setName("History kept " + UUID.randomUUID());
        event.setEventDate(LocalDate.of(2026, 1, 1));
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        Long eventId = eventRepository.save(event).getId();
        RacingClass racingClass = new RacingClass();
        racingClass.setName("History class " + UUID.randomUUID());
        racingClass.setCreatedAt(Instant.now());
        racingClass.setUpdatedAt(Instant.now());
        Long racingClassId = racingClassRepository.save(racingClass).getId();
        Long eventClassId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        Round round = new Round();
        round.setEventId(eventId);
        round.setType(RoundType.FINAL);
        round.setRoundNumber(1);
        round.setSequenceInEvent(1);
        round.setCreatedAt(Instant.now());
        round.setUpdatedAt(Instant.now());
        Long roundId = roundRepository.save(round).getId();
        Race race = new Race();
        race.setRoundId(roundId);
        race.setEventClassId(eventClassId);
        race.setHeatNumber(1);
        race.setSequenceInRound(1);
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.FINISHED);
        race.setCreatedAt(Instant.now());
        race.setUpdatedAt(Instant.now());
        Long raceId = raceRepository.save(race).getId();
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setRaceId(raceId);
        snapshot.setPositionsJson("[]");
        snapshot.setLapHistoryJson("[]");
        snapshot.setFinishedAt(Instant.now());
        snapshot.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snapshot);
        return () -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(RESULT_SNAPSHOTS).where(RESULT_SNAPSHOTS.RACE_ID.eq(raceId)).execute());
            raceRepository.deleteById(raceId);
            roundRepository.deleteById(roundId);
            dsl.transaction(tx -> tx.dsl().deleteFrom(EVENT_CLASSES).where(EVENT_CLASSES.ID.eq(eventClassId)).execute());
            racingClassRepository.deleteById(racingClassId);
            eventRepository.deleteById(eventId);
        };
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : HISTORY_TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
        return counts;
    }

    @Test
    void theNightlyCleanUpRemovesNoRaceLapOrResultRows() {
        Runnable cleanUp = seedHistory();
        try {
            Map<String, Integer> before = counts();
            assertThat(before.get("events")).isPositive();
            assertThat(before.get("races")).isPositive();
            assertThat(before.get("result_snapshots")).isPositive();

            retention.purge();

            assertThat(counts()).isEqualTo(before);
        } finally {
            cleanUp.run();
        }
    }
}
