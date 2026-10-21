package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The officials' printouts read an event's finished races with their real names and starting grid. */
class EventResultsQueryIT extends AbstractIntegrationTest {

    @Autowired EventResultsQuery query;
    @Autowired JdbcTemplate jdbc;

    private String run;
    private long eventId;
    private long eventClassId;
    private long competitorId;
    private long entryId;
    private long qualifierId;
    private long unfinishedId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'IN_PROGRESS') returning id""", Long.class, "Results " + run);
        long racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Results Buggy " + run);
        eventClassId = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED","durationMinutes":5}') returning id""",
                Long.class, eventId, racingClassId);
        competitorId = jdbc.queryForObject("insert into competitors (display_name) values (?) returning id",
                Long.class, "Ada Lovelace " + run);
        entryId = jdbc.queryForObject("""
                insert into entries (competitor_id, event_id, event_class_id, transponder_number, status)
                values (?, ?, ?, ?, 'CONFIRMED') returning id""",
                Long.class, competitorId, eventId, eventClassId, "93" + run);
        long qualifying = round(1, "QUALIFIER");
        long finals = round(2, "FINAL");
        qualifierId = race(qualifying, 1, 1, null);
        unfinishedId = race(qualifying, 2, 2, null);
        long finalId = race(finals, 1, 3, "A");
        jdbc.update("insert into race_entries (race_id, entry_id, grid_position) values (?, ?, 3)",
                qualifierId, entryId);
        snapshot(qualifierId);
        snapshot(finalId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from result_snapshots where race_id in "
                + "(select id from races where round_id in (select id from rounds where event_id = ?))", eventId);
        jdbc.update("delete from race_entries where race_id in "
                + "(select id from races where round_id in (select id from rounds where event_id = ?))", eventId);
        jdbc.update("delete from races where round_id in (select id from rounds where event_id = ?)", eventId);
        jdbc.update("delete from rounds where event_id = ?", eventId);
        jdbc.update("delete from entries where event_id = ?", eventId);
        long racingClassId = jdbc.queryForObject(
                "select racing_class_id from event_classes where id = ?", Long.class, eventClassId);
        jdbc.update("delete from event_classes where event_id = ?", eventId);
        jdbc.update("delete from racing_classes where id = ?", racingClassId);
        jdbc.update("delete from competitors where id = ?", competitorId);
        jdbc.update("delete from events where id = ?", eventId);
    }

    @Test
    void listsOnlyFinishedRacesInRunOrderWithTheirGrid() {
        EventResultsDto results = query.forEvent(eventId);

        assertThat(results.eventName()).isEqualTo("Results " + run);
        List<EventResultsDto.Race> races = results.races();
        assertThat(races).extracting(EventResultsDto.Race::roundType).containsExactly("QUALIFIER", "FINAL");
        assertThat(races).extracting(EventResultsDto.Race::raceId).doesNotContain(unfinishedId);
        assertThat(races.get(1).finalLetter()).isEqualTo("A");
        assertThat(races.get(0).startPositions()).containsEntry(entryId, 3);
        assertThat(races.get(1).startPositions()).isEmpty();
    }

    @Test
    void oneRaceComesBackWithItsGridAndAnUnfinishedRaceIsNotFound() {
        EventResultsDto results = query.forRace(qualifierId);

        assertThat(results.races()).hasSize(1);
        assertThat(results.races().get(0).startPositions()).containsEntry(entryId, 3);
        assertThatThrownBy(() -> query.forRace(unfinishedId)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> query.forEvent(-1L)).isInstanceOf(EntityNotFoundException.class);
    }

    private long round(int number, String type) {
        return jdbc.queryForObject("""
                insert into rounds (event_id, type, round_number, sequence_in_event)
                values (?, ?, ?, ?) returning id""", Long.class, eventId, type, number, number);
    }

    private long race(long roundId, int heat, int sequenceInRound, String finalLetter) {
        return jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, start_type, status,
                                   final_letter)
                values (?, ?, ?, ?, 'STAGGER', 'FINISHED', ?) returning id""",
                Long.class, roundId, eventClassId, heat, sequenceInRound, finalLetter);
    }

    private void snapshot(long raceId) {
        jdbc.update("""
                insert into result_snapshots (race_id, finished_at, positions_json, lap_history_json)
                values (?, 1760000000000000, '[]', '[]')""", raceId);
    }
}
