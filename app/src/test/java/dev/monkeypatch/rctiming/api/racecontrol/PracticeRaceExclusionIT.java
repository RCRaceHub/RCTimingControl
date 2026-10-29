package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Practice and a live race share the one decoder, so a running race stops practice and blocks it starting. */
class PracticeRaceExclusionIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PracticeSessionService sessionService;
    @Autowired PracticeSessionRepository sessionRepository;

    private long eventId;
    private long roundId;
    private long eventClassId;
    private long racingClassId;
    private long directorId;
    private String token;

    @BeforeEach
    void setUp() {
        String run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        finishLeftoverRunningRaces();
        sessionRepository.findRunningSession().ifPresent(r -> sessionService.stop(Actor.system("test"), r.getId()));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'IN_PROGRESS') returning id""", Long.class, "Practice vs race " + run);
        racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Exclusion Buggy " + run);
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
        sessionRepository.findRunningSession().ifPresent(r -> sessionService.stop(Actor.system("test"), r.getId()));
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        jdbc.update("delete from audit_log where actor_user_id = ?", directorId);
        jdbc.update("delete from audit_log where entity_type = 'practice_session' and entity_id in "
                + "(select cast(id as text) from practice_sessions where created_by_user_id = ?)", directorId);
        jdbc.update("delete from practice_laps where practice_session_id in "
                + "(select id from practice_sessions where created_by_user_id = ?)", directorId);
        jdbc.update("delete from practice_sessions where created_by_user_id = ?", directorId);
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
    void practiceCannotStartWhileARaceIsRunning() {
        race("RUNNING");
        Object practice = createPractice();

        ResponseEntity<Map> started = practice("/" + practice + "/start");

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(practiceStatus(practice)).isEqualTo("IDLE");
    }

    @Test
    void anIdleSessionReportsWhetherARaceIsRunning() {
        Object practice = createPractice();
        assertThat(getPractice(practice).get("raceRunning")).isEqualTo(false);

        race("RUNNING");

        assertThat(getPractice(practice).get("raceRunning")).isEqualTo(true);
    }

    @Test
    void startingARaceStopsARunningPracticeSession() {
        Object practice = createPractice();
        assertThat(practice("/" + practice + "/start").getStatusCode()).isEqualTo(HttpStatus.OK);
        long race = race("PENDING");

        command(race, "call-grid");
        assertThat(practiceStatus(practice)).as("a grid call does not stop practice").isEqualTo("RUNNING");
        command(race, "start");

        assertThat(practiceStatus(practice)).isEqualTo("STOPPED");
        // The system stopped it, and the log says so
        var rows = jdbc.queryForList("select * from audit_log where entity_type = 'practice_session' "
                + "and entity_id = ? and action = 'PRACTICE_SESSION_STOPPED'", String.valueOf(practice));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_label")).isEqualTo("system:race-start");
        assertThat(((Number) rows.get(0).get("race_id")).longValue()).isEqualTo(race);
        assertThat(rows.get(0).get("summary").toString()).contains("because race " + race + " started");
    }

    @Test
    void startingARaceStopsEveryRunningPracticeSessionEvenIfTwoWereLeftRunning() {
        Object first = createPractice();
        Object second = createPractice();
        assertThat(practice("/" + first + "/start").getStatusCode()).isEqualTo(HttpStatus.OK);
        // Nothing stops a second one being started, so make sure the race still copes
        jdbc.update("update practice_sessions set status = 'RUNNING' where id = ?", second);
        long race = race("PENDING");

        command(race, "call-grid");
        command(race, "start");

        assertThat(practiceStatus(first)).isEqualTo("STOPPED");
        assertThat(practiceStatus(second)).isEqualTo("STOPPED");
    }

    @Test
    void resumingARaceStopsPracticeStartedWhileItWasPaused() {
        long race = race("PENDING");
        command(race, "call-grid");
        command(race, "start");
        command(race, "stop");
        Object practice = createPractice();
        assertThat(practice("/" + practice + "/start").getStatusCode())
                .as("a paused race does not block practice").isEqualTo(HttpStatus.OK);

        command(race, "start");

        assertThat(practiceStatus(practice)).isEqualTo("STOPPED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Object createPractice() {
        ResponseEntity<Map> created = restTemplate.exchange("/api/v1/practice-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Exclusion practice", "bestLapN", 3), headers()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().get("id");
    }

    private ResponseEntity<Map> practice(String path) {
        return restTemplate.exchange("/api/v1/practice-sessions" + path, HttpMethod.POST,
                new HttpEntity<>(headers()), Map.class);
    }

    private Map getPractice(Object id) {
        return restTemplate.exchange("/api/v1/practice-sessions/" + id, HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class).getBody();
    }

    private String practiceStatus(Object id) {
        return jdbc.queryForObject("select status from practice_sessions where id = ?", String.class, id);
    }

    private long race(String status) {
        return jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, final_letter,
                                   start_type, status)
                values (?, ?, 1, 1, 'A', 'GRID', ?) returning id""", Long.class, roundId, eventClassId, status);
    }

    private void command(long raceId, String command) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/race-control/race/" + raceId + "/" + command, HttpMethod.POST,
                new HttpEntity<>(headers()), String.class);
        assertThat(response.getStatusCode()).as(command).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String loginAsDirector() {
        User user = new User();
        user.setEmail("practice-race-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Practice");
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
