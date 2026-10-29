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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Creating, starting and stopping a practice session, and linking a transponder in it, name the official (#139). */
class PracticeSessionAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PracticeSessionService sessionService;
    @Autowired PracticeSessionRepository sessionRepository;

    private String run;
    private long directorId;
    private String token;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        finishLeftoverRunningRaces();
        // Only one session runs at a time, and another test may have left one going
        sessionRepository.findRunningSession().ifPresent(r -> sessionService.stop(Actor.system("test"), r.getId()));
        token = loginAsDirector();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where actor_user_id = ?", directorId);
        jdbc.update("delete from practice_drivers where practice_session_id in "
                + "(select id from practice_sessions where created_by_user_id = ?)", directorId);
        jdbc.update("delete from competitors where display_name = ?", "Pat Practice " + run);
        jdbc.update("delete from practice_laps where practice_session_id in "
                + "(select id from practice_sessions where created_by_user_id = ?)", directorId);
        jdbc.update("delete from practice_sessions where created_by_user_id = ?", directorId);
        jdbc.update("delete from refresh_tokens where user_id = ?", directorId);
        jdbc.update("delete from user_roles where user_id = ?", directorId);
        jdbc.update("delete from users where id = ?", directorId);
    }

    @Test
    void createStartLinkAndStopAreRecordedAndTheCreatorIsKept() {
        ResponseEntity<Map> created = send("POST", "/api/v1/practice-sessions",
                Map.of("name", "Audit practice " + run, "bestLapN", 3));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object id = created.getBody().get("id");
        assertThat(send("POST", "/api/v1/practice-sessions/" + id + "/start", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send("POST", "/api/v1/practice-sessions/" + id + "/link-transponder",
                Map.of("transponderNumber", "T" + run, "newName", "Pat Practice " + run)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send("POST", "/api/v1/practice-sessions/" + id + "/stop", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> rows = jdbc.queryForList("select * from audit_log where actor_user_id = ? "
                + "and entity_type = 'practice_session' and entity_id = ? order by id", directorId, String.valueOf(id));
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "PRACTICE_SESSION_CREATED", "PRACTICE_SESSION_STARTED", "PRACTICE_TRANSPONDER_LINKED",
                "PRACTICE_SESSION_STOPPED");
        assertThat(rows.get(0).get("after_json").toString()).contains("Audit practice " + run);
        assertThat(rows.get(1).get("before_json")).isEqualTo("\"IDLE\"");
        assertThat(rows.get(1).get("after_json")).isEqualTo("\"RUNNING\"");
        assertThat(rows.get(2).get("summary").toString()).contains("T" + run).contains("Pat Practice " + run);
        assertThat(rows.get(3).get("after_json")).isEqualTo("\"STOPPED\"");
        // Who created the session is kept on it, which was always empty before
        assertThat(jdbc.queryForObject("select created_by_user_id from practice_sessions where id = ?", Long.class, id))
                .isEqualTo(directorId);
    }

    @Test
    void aRefusedStartLeavesNoRow() {
        Object id = send("POST", "/api/v1/practice-sessions", Map.of("name", "Refused " + run, "bestLapN", 3))
                .getBody().get("id");
        send("POST", "/api/v1/practice-sessions/" + id + "/start", null);

        ResponseEntity<Map> again = send("POST", "/api/v1/practice-sessions/" + id + "/start", null);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where actor_user_id = ? "
                + "and action = 'PRACTICE_SESSION_STARTED' and entity_id = ?", Integer.class, directorId,
                String.valueOf(id))).isEqualTo(1);
        send("POST", "/api/v1/practice-sessions/" + id + "/stop", null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Map> send(String method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), Map.class);
    }

    private String loginAsDirector() {
        User user = new User();
        user.setEmail("practice-audit-" + UUID.randomUUID() + "@example.com");
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
