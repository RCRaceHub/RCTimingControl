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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Creating, editing and moving an event, and setting up its classes and run order, name the official (#139). */
class EventAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String run;
    private long adminId;
    private String token;
    private long templateId;
    private long racingClassId;
    private long secondClassId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        templateId = jdbc.queryForObject("""
                insert into race_format_templates (name, config)
                values (?, '{"type":"TIMED","durationMinutes":5}') returning id""", Long.class, "Audit format " + run);
        racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Audit Buggy " + run);
        secondClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Audit Truggy " + run);
        token = loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where actor_user_id = ?", adminId);
        jdbc.update("delete from audit_log where summary like ?", "%" + run + "%");
        jdbc.update("delete from entries where event_id in (select id from events where name like ?)",
                "%" + run + "%");
        jdbc.update("delete from events where name like ?", "%" + run + "%");
        jdbc.update("delete from competitors where display_name = ?", "Audit driver " + run);
        jdbc.update("delete from racing_classes where id in (?, ?)", racingClassId, secondClassId);
        jdbc.update("delete from race_format_templates where id = ?", templateId);
        jdbc.update("delete from refresh_tokens where user_id = ?", adminId);
        jdbc.update("delete from user_roles where user_id = ?", adminId);
        jdbc.update("delete from users where id = ?", adminId);
    }

    @Test
    void creatingEditingAndMovingAnEventAreRecordedWithTheirValues() {
        long eventId = createEvent("Club day " + run);

        assertThat(send("PUT", "/api/v1/admin/events/" + eventId,
                Map.of("name", "Club day moved " + run, "eventDate", "2026-11-01")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        for (String status : List.of("PUBLISHED", "OPEN", "ENTRIES_CLOSED", "IN_PROGRESS")) {
            transition(eventId, status);
        }

        List<Map<String, Object>> rows = rows(eventId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "EVENT_CREATED", "EVENT_UPDATED", "EVENT_PUBLISHED", "EVENT_OPEN", "EVENT_ENTRIES_CLOSED",
                "EVENT_IN_PROGRESS");
        assertThat(rows).allSatisfy(r ->
                assertThat(((Number) r.get("actor_user_id")).longValue()).isEqualTo(adminId));
        Map<String, Object> update = rows.get(1);
        assertThat(update.get("before_json").toString()).contains("Club day " + run).contains("2026-10-31");
        assertThat(update.get("after_json").toString()).contains("Club day moved " + run).contains("2026-11-01");
        assertThat(rows.get(2).get("before_json")).isEqualTo("\"DRAFT\"");
        assertThat(rows.get(2).get("after_json")).isEqualTo("\"PUBLISHED\"");
    }

    @Test
    void completingTheDayRecordsThatABackupAndAnExportFollow() {
        long eventId = createEvent("Day close " + run);
        jdbc.update("update events set status = 'IN_PROGRESS' where id = ?", eventId);

        transition(eventId, "COMPLETED");

        Map<String, Object> row = rows(eventId).get(1);
        assertThat(row.get("action")).isEqualTo("EVENT_COMPLETED");
        assertThat(row.get("summary").toString()).contains("backup").contains("results export");
    }

    @Test
    void aRefusedTransitionLeavesNoRow() {
        long eventId = createEvent("Refused " + run);

        ResponseEntity<String> refused = send("POST", "/api/v1/admin/events/" + eventId + "/transition",
                Map.of("targetStatus", "COMPLETED"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rows(eventId)).extracting(r -> r.get("action")).containsExactly("EVENT_CREATED");
    }

    @Test
    void addingOverridingAndCombiningClassesAreRecorded() {
        long eventId = createEvent("Classes " + run);

        long first = addClass(eventId, racingClassId);
        long second = addClass(eventId, secondClassId);
        assertThat(send("PUT", "/api/v1/admin/events/" + eventId + "/classes/" + first + "/overrides",
                Map.of("override", Map.of("durationMinutes", 8))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send("PUT", "/api/v1/admin/events/" + eventId + "/classes/" + first + "/overrides",
                Map.of("override", Map.of())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send("POST", "/api/v1/admin/events/" + eventId + "/classes/combine",
                Map.of("eventClassIds", List.of(first, second))).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> rows = rows(eventId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "EVENT_CREATED", "EVENT_CLASS_ADDED", "EVENT_CLASS_ADDED", "EVENT_CLASS_OVERRIDES_CHANGED",
                "EVENT_CLASS_OVERRIDES_CHANGED", "EVENT_CLASSES_COMBINED");
        assertThat(rows.get(1).get("summary").toString()).contains("Audit Buggy " + run).contains("Audit format " + run);
        assertThat(rows.get(3).get("after_json").toString()).contains("durationMinutes");
        assertThat(rows.get(4).get("summary").toString()).startsWith("Cleared the format overrides");
        assertThat(rows.get(4).get("before_json").toString()).contains("durationMinutes");
        assertThat(rows.get(5).get("summary").toString()).contains("Audit Buggy " + run).contains("Audit Truggy " + run);
    }

    @Test
    void generatingTheRunOrderIsRecordedAndSeedingWithoutResultsIsRefused() {
        long eventId = createEvent("Run order " + run);
        long eventClassId = addClass(eventId, racingClassId);
        addDriver(eventId, eventClassId);

        ResponseEntity<String> generated = send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds",
                Map.of("practiceRoundsCount", 0, "qualifyingRoundsCount", 1, "maxCarsPerHeat", 10,
                        "classFinalsConfigs", List.of(Map.of("eventClassId", eventClassId, "finalsCount", 1))));
        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> seeded = send("POST", "/api/v1/admin/events/" + eventId + "/seed-finals",
                Map.of("eventClassId", eventClassId, "finalsCount", 1, "carsPerFinal", 10, "bumpCount", 0));
        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        List<Map<String, Object>> rows = rows(eventId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "EVENT_CREATED", "EVENT_CLASS_ADDED", "RUN_ORDER_GENERATED");
        assertThat(rows.get(2).get("summary").toString()).contains("Run order " + run).contains("1 qualifying");
        assertThat(((Number) rows.get(2).get("actor_user_id")).longValue()).isEqualTo(adminId);
    }

    @Test
    void aSecondRunOrderIsRefusedAndLeavesNoRow() {
        long eventId = createEvent("Twice " + run);
        addDriver(eventId, addClass(eventId, racingClassId));
        Map<String, Object> body = Map.of("practiceRoundsCount", 0, "qualifyingRoundsCount", 1,
                "maxCarsPerHeat", 10, "classFinalsConfigs", List.of());
        assertThat(send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds", body).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> again = send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds", body);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rows(eventId)).extracting(r -> r.get("action")).containsExactly("EVENT_CREATED", "EVENT_CLASS_ADDED", "RUN_ORDER_GENERATED");
    }

    @Test
    void regeneratingReplacesThePendingRunOrderAndIsRecorded() {
        long eventId = createEvent("Redo " + run);
        addDriver(eventId, addClass(eventId, racingClassId));
        Map<String, Object> first = Map.of("practiceRoundsCount", 0, "qualifyingRoundsCount", 1,
                "maxCarsPerHeat", 10, "classFinalsConfigs", List.of());
        Map<String, Object> second = Map.of("practiceRoundsCount", 1, "qualifyingRoundsCount", 2,
                "maxCarsPerHeat", 10, "classFinalsConfigs", List.of());
        assertThat(send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds", first).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(send("POST", "/api/v1/admin/events/" + eventId + "/regenerate-rounds", second).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = rows(eventId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "EVENT_CREATED", "EVENT_CLASS_ADDED", "RUN_ORDER_GENERATED", "RUN_ORDER_REGENERATED");
        assertThat(rows.get(3).get("summary").toString()).startsWith("Regenerated the run order")
                .contains("1 practice and 2 qualifying");
    }

    @Test
    void generatingRounds_withAClassConfigMissingItsClass_isRefusedAndGeneratesNothing() {
        long eventId = createEvent("No class " + run);
        Map<String, Object> config = new HashMap<>();
        config.put("eventClassId", null);
        config.put("finalsCount", 1);

        ResponseEntity<String> refused = send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds",
                Map.of("practiceRoundsCount", 0, "qualifyingRoundsCount", 1, "maxCarsPerHeat", 10,
                        "classFinalsConfigs", List.of(config)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rows(eventId)).extracting(r -> r.get("action")).containsExactly("EVENT_CREATED");
    }

    @Test
    void generatingRounds_withMoreFinalsThanLetters_isRefusedAndGeneratesNothing() {
        long eventId = createEvent("Too many finals " + run);
        long eventClassId = addClass(eventId, racingClassId);

        ResponseEntity<String> refused = send("POST", "/api/v1/admin/events/" + eventId + "/generate-rounds",
                Map.of("practiceRoundsCount", 0, "qualifyingRoundsCount", 1, "maxCarsPerHeat", 10,
                        "classFinalsConfigs", List.of(Map.of("eventClassId", eventClassId, "finalsCount", 27))));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rows(eventId)).extracting(r -> r.get("action"))
                .containsExactly("EVENT_CREATED", "EVENT_CLASS_ADDED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long createEvent(String name) {
        ResponseEntity<Map> created = restTemplate.exchange("/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "eventDate", "2026-10-31"), headers()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    private void transition(long eventId, String target) {
        assertThat(send("POST", "/api/v1/admin/events/" + eventId + "/transition",
                Map.of("targetStatus", target)).getStatusCode()).as(target).isEqualTo(HttpStatus.OK);
    }

    private long addClass(long eventId, long racingClass) {
        ResponseEntity<Map> added = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/classes",
                HttpMethod.POST, new HttpEntity<>(Map.of("racingClassId", racingClass, "templateId", templateId),
                        headers()), Map.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) added.getBody().get("id")).longValue();
    }

    /** A confirmed entry, since a run order can't be generated for a class nobody has entered. */
    private void addDriver(long eventId, long eventClassId) {
        long competitorId = jdbc.queryForObject("insert into competitors (display_name) values (?) returning id",
                Long.class, "Audit driver " + run);
        jdbc.update("""
                insert into entries (event_id, event_class_id, competitor_id, transponder_number, status)
                values (?, ?, ?, ?, 'CONFIRMED')""", eventId, eventClassId, competitorId, "9" + run);
    }

    private ResponseEntity<String> send(String method, String url, Object body) {
        return restTemplate.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers()), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private List<Map<String, Object>> rows(long eventId) {
        return jdbc.queryForList("select * from audit_log where event_id = ? order by id", eventId);
    }

    private String loginAsAdmin() {
        User user = new User();
        user.setEmail("event-audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Event");
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
