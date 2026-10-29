package dev.monkeypatch.rctiming.api.admin;

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
import org.springframework.http.MediaType;
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

/** Tracks, their decoder loops and lap thresholds, classes and race formats name who changed them (#139). */
class SetupConfigAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String run;
    private long adminId;
    private String token;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        token = loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where actor_user_id = ?", adminId);
        jdbc.update("delete from track_lap_thresholds where track_id in (select id from tracks where name like ?)",
                "%" + run + "%");
        jdbc.update("delete from decoder_loops where track_id in (select id from tracks where name like ?)",
                "%" + run + "%");
        jdbc.update("delete from tracks where name like ?", "%" + run + "%");
        jdbc.update("delete from racing_classes where name like ?", "%" + run + "%");
        jdbc.update("delete from race_format_templates where name like ?", "%" + run + "%");
        jdbc.update("delete from refresh_tokens where user_id = ?", adminId);
        jdbc.update("delete from user_roles where user_id = ?", adminId);
        jdbc.update("delete from users where id = ?", adminId);
    }

    @Test
    void tracksLoopsAndThresholdsAreRecorded() {
        Object classId = send("POST", "/api/v1/admin/classes", Map.of("name", "Threshold class " + run))
                .getBody().get("id");
        ResponseEntity<Map> created = send("POST", "/api/v1/admin/tracks",
                Map.of("name", "Audit track " + run, "venueNotes", "first", "trackLength", 120.5));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object trackId = created.getBody().get("id");
        send("PUT", "/api/v1/admin/tracks/" + trackId,
                Map.of("name", "Audit track " + run, "venueNotes", "second", "trackLength", 120.5));
        ResponseEntity<Map> loop = send("POST", "/api/v1/admin/tracks/" + trackId + "/loops",
                Map.of("loopId", "L1", "displayName", "Main", "loopType", "FINISH_LINE", "isScoringLoop", true));
        Object loopId = loop.getBody().get("id");
        send("PUT", "/api/v1/admin/tracks/loops/" + loopId,
                Map.of("loopId", "L1", "displayName", "Main line", "loopType", "FINISH_LINE", "isScoringLoop", true));
        ResponseEntity<Map> threshold = send("POST", "/api/v1/admin/tracks/" + trackId + "/thresholds",
                Map.of("racingClassId", classId, "minLapMs", 5000));
        send("POST", "/api/v1/admin/tracks/" + trackId + "/thresholds",
                Map.of("racingClassId", classId, "minLapMs", 6000));
        Object thresholdId = threshold.getBody().get("id");
        assertThat(send("DELETE", "/api/v1/admin/tracks/thresholds/" + thresholdId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send("DELETE", "/api/v1/admin/tracks/loops/" + loopId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send("DELETE", "/api/v1/admin/tracks/" + trackId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = mine("select * from audit_log where actor_user_id = ? "
                + "and entity_type in ('track', 'decoder_loop', 'lap_threshold') order by id");
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "TRACK_CREATED", "TRACK_UPDATED", "DECODER_LOOP_ADDED", "DECODER_LOOP_UPDATED",
                "LAP_THRESHOLD_ADDED", "LAP_THRESHOLD_CHANGED", "LAP_THRESHOLD_DELETED", "DECODER_LOOP_DELETED",
                "TRACK_DELETED");
        assertThat(rows.get(1).get("before_json").toString()).contains("first");
        assertThat(rows.get(1).get("after_json").toString()).contains("second");
        assertThat(rows.get(3).get("before_json").toString()).contains("\"displayName\":\"Main\"");
        assertThat(rows.get(3).get("summary").toString()).contains("Audit track " + run);
        assertThat(rows.get(5).get("before_json").toString()).contains("\"minLapMs\":5000");
        assertThat(rows.get(5).get("after_json").toString()).contains("\"minLapMs\":6000");
        assertThat(rows.get(5).get("summary").toString()).contains("Threshold class " + run);
        assertThat(rows.get(8).get("summary").toString()).contains("Audit track " + run);
        assertThat(rows.get(8).get("before_json").toString()).contains("second");
    }

    @Test
    void classesAreRecordedWhenAddedChangedAndRemoved() {
        ResponseEntity<Map> created = send("POST", "/api/v1/admin/classes",
                Map.of("name", "Audit class " + run, "description", "before"));
        Object id = created.getBody().get("id");
        send("PUT", "/api/v1/admin/classes/" + id, Map.of("name", "Audit class " + run, "description", "after"));
        assertThat(send("DELETE", "/api/v1/admin/classes/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = mine("select * from audit_log where actor_user_id = ? "
                + "and entity_type = 'racing_class' order by id");
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "CLASS_CREATED", "CLASS_UPDATED", "CLASS_DELETED");
        assertThat(rows.get(1).get("before_json").toString()).contains("before");
        assertThat(rows.get(1).get("after_json").toString()).contains("after");
        assertThat(rows.get(2).get("summary").toString()).contains("Audit class " + run);
    }

    @Test
    void raceFormatsAreRecordedWhenAddedChangedImportedAndRemoved() {
        Map<String, Object> five = Map.of("type", "TIMED", "durationMinutes", 5, "startType", "STAGGER",
                "qualifyingType", "FTQ", "racePaddingMinutes", 2, "staggerIntervalSeconds", 3);
        Map<String, Object> eight = Map.of("type", "TIMED", "durationMinutes", 8, "startType", "STAGGER",
                "qualifyingType", "FTQ", "racePaddingMinutes", 2, "staggerIntervalSeconds", 3);
        Object id = send("POST", "/api/v1/admin/formats", Map.of("name", "Audit format " + run, "config", five))
                .getBody().get("id");
        send("PUT", "/api/v1/admin/formats/" + id, Map.of("name", "Audit format " + run, "config", eight));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> imported = restTemplate.exchange("/api/v1/admin/formats/import?name=Audit import " + run,
                HttpMethod.POST, new HttpEntity<>("""
                        {"type":"TIMED","durationMinutes":6,"startType":"STAGGER","qualifyingType":"FTQ",
                         "racePaddingMinutes":2,"staggerIntervalSeconds":3}""", headers), Map.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send("DELETE", "/api/v1/admin/formats/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = mine("select * from audit_log where actor_user_id = ? "
                + "and entity_type = 'race_format' order by id");
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "FORMAT_CREATED", "FORMAT_UPDATED", "FORMAT_IMPORTED", "FORMAT_DELETED");
        assertThat(rows.get(1).get("before_json").toString()).contains("\"durationMinutes\":5");
        assertThat(rows.get(1).get("after_json").toString()).contains("\"durationMinutes\":8");
        assertThat(rows.get(2).get("summary").toString()).contains("Audit import " + run);
        assertThat(rows.get(3).get("before_json").toString()).contains("\"durationMinutes\":8");
    }

    @Test
    void aRefusedChangeLeavesNoRow() {
        ResponseEntity<Map> missing = send("PUT", "/api/v1/admin/classes/999999999",
                Map.of("name", "Nope " + run, "description", "x"));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mine("select * from audit_log where actor_user_id = ? and entity_type = 'racing_class'")).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> mine(String sql) {
        return jdbc.queryForList(sql, adminId);
    }

    private ResponseEntity<Map> send(String method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), Map.class);
    }

    private String loginAsAdmin() {
        User user = new User();
        user.setEmail("setup-audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Setup");
        user.setLastName("Admin");
        user.setRoles(Set.of(Role.ADMIN));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        adminId = user.getId();
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
