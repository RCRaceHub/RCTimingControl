package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Every race lifecycle command is written to the audit log with who gave it, in the command's own transaction (#139). */
class RaceControlAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private long eventId;
    private long roundId;
    private long eventClassId;
    private long racingClassId;
    private long directorId;
    private String token;

    @BeforeEach
    void setUp() {
        String run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'IN_PROGRESS') returning id""", Long.class, "Race audit " + run);
        racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Audit Buggy " + run);
        eventClassId = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED","durationMinutes":5}') returning id""",
                Long.class, eventId, racingClassId);
        roundId = jdbc.queryForObject("""
                insert into rounds (event_id, type, round_number, sequence_in_event)
                values (?, 'FINAL', 1, 1) returning id""", Long.class, eventId);
        token = loginAsDirector();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        jdbc.update("delete from audit_log where actor_user_id = ?", directorId);
        jdbc.update("delete from races where round_id = ?", roundId);
        jdbc.update("delete from rounds where event_id = ?", eventId);
        jdbc.update("delete from event_classes where event_id = ?", eventId);
        jdbc.update("delete from racing_classes where id = ?", racingClassId);
        jdbc.update("delete from events where id = ?", eventId);
        jdbc.update("delete from refresh_tokens where user_id = ?", directorId);
        jdbc.update("delete from user_roles where user_id = ?", directorId);
        jdbc.update("delete from users where id = ?", directorId);
    }

    @Test
    void eachLifecycleCommandIsRecordedWithWhoGaveIt() {
        long race = race("PENDING");

        command(race, "call-grid");
        command(race, "start");
        command(race, "stop");
        command(race, "start");
        command(race, "finish");

        List<Map<String, Object>> rows = auditRows(race);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "RACE_GRID_CALLED", "RACE_STARTED", "RACE_STOPPED", "RACE_RESUMED", "RACE_FINISHED");
        assertThat(rows).allSatisfy(r -> {
            assertThat(((Number) r.get("actor_user_id")).longValue()).isEqualTo(directorId);
            assertThat(r.get("actor_label").toString()).contains("Race Director");
            assertThat(((Number) r.get("event_id")).longValue()).isEqualTo(eventId);
            assertThat(r.get("entity_type")).isEqualTo("race");
        });
        // The row says what changed: the status before and after, as JSON
        assertThat(rows.get(0).get("before_json")).isEqualTo("\"PENDING\"");
        assertThat(rows.get(0).get("after_json")).isEqualTo("\"GRID\"");
        assertThat(rows.get(3).get("before_json")).isEqualTo("\"STOPPED\"");
        assertThat(rows.get(3).get("after_json")).isEqualTo("\"RUNNING\"");
        assertThat(rows.get(4).get("summary").toString()).startsWith("Finished A final (race " + race + ")");
    }

    @Test
    void abandoningARaceIsRecorded() {
        long race = race("RUNNING");

        command(race, "abandon");

        List<Map<String, Object>> rows = auditRows(race);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("RACE_ABANDONED");
        assertThat(rows.get(0).get("before_json")).isEqualTo("\"RUNNING\"");
        assertThat(rows.get(0).get("after_json")).isEqualTo("\"FINISHED\"");
    }

    @Test
    void restartingARaceRecordsWhatItThrewAway() {
        long race = race("FINISHED");
        jdbc.update("update races set finished_at = ?, abandoned_at = ? where id = ?", 1_000_000L, 1_000_000L, race);
        jdbc.update("""
                insert into result_snapshots (race_id, finished_at, positions_json, lap_history_json)
                values (?, 1000000, '[{"entryId":7,"position":1}]', '[]')""", race);

        command(race, "restart");

        List<Map<String, Object>> rows = auditRows(race);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("RACE_RESTARTED");
        String before = rows.get(0).get("before_json").toString();
        assertThat(before).contains("\"status\":\"FINISHED\"");
        assertThat(before).contains("\"abandonedAt\"");
        // The stored result that was deleted, so it can be seen what was lost
        assertThat(before).contains("resultSnapshot").contains("entryId");
        assertThat(rows.get(0).get("after_json").toString()).contains("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from result_snapshots where race_id = ?", Integer.class, race))
                .isZero();
    }

    @Test
    void aRefusedCommandLeavesNoRow() {
        long race = race("FINISHED");

        ResponseEntity<String> refused = post(race, "start");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(auditRows(race)).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long race(String status) {
        return jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, final_letter,
                                   start_type, status)
                values (?, ?, 1, 1, 'A', 'GRID', ?) returning id""", Long.class, roundId, eventClassId, status);
    }

    private void command(long raceId, String command) {
        assertThat(post(raceId, command).getStatusCode()).as(command).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> post(long raceId, String command) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/v1/race-control/race/" + raceId + "/" + command, HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
    }

    private List<Map<String, Object>> auditRows(long raceId) {
        return jdbc.queryForList("select * from audit_log where race_id = ? order by id", raceId);
    }

    private String loginAsDirector() {
        User user = new User();
        user.setEmail("rc-audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Race");
        user.setLastName("Director");
        user.setRoles(Set.of(Role.RACE_DIRECTOR));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        directorId = user.getId();
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
