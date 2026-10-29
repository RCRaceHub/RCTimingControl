package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Changes to a championship, and the exclusions that take points away, are in the audit log with who made them (#139). */
class ChampionshipAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private String run;
    private long adminId;
    private long refereeId;
    private String adminToken;
    private String refereeToken;
    private long eventId;
    private long racingClassId;
    private long competitorId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        adminId = createOfficial("Ada", "Admin", Set.of(Role.ADMIN));
        refereeId = createOfficial("Ray", "Referee", Set.of(Role.REFEREE));
        adminToken = login(adminId);
        refereeToken = login(refereeId);
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'IN_PROGRESS') returning id""", Long.class, "Round one " + run);
        racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Mod Buggy " + run);
        competitorId = jdbc.queryForObject("insert into competitors (display_name) values (?) returning id",
                Long.class, "Dan Driver " + run);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where entity_type = 'championship' and actor_user_id in (?, ?)", adminId, refereeId);
        jdbc.update("delete from championships where name like ?", "%" + run);
        jdbc.update("delete from competitors where id = ?", competitorId);
        jdbc.update("delete from racing_classes where id = ?", racingClassId);
        jdbc.update("delete from events where id = ?", eventId);
        for (long id : new long[]{adminId, refereeId}) {
            jdbc.update("delete from refresh_tokens where user_id = ?", id);
            jdbc.update("delete from user_roles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
    }

    @Test
    void everyChangeToAChampionshipIsRecordedWithWhoMadeIt() throws Exception {
        long id = create("Club Championship " + run, 4, 6, 2, 3);
        send(HttpMethod.PUT, "/" + id, adminToken, Map.of("name", "Club Championship " + run, "bestXFromYX", 5,
                "bestXFromYY", 6, "tqBonusPoints", 2, "afinalWinnerBonusPoints", 3));
        send(HttpMethod.POST, "/" + id + "/classes", adminToken, Map.of("racingClassId", racingClassId));
        send(HttpMethod.POST, "/" + id + "/events", adminToken, Map.of("eventId", eventId, "roundNumber", 1));
        send(HttpMethod.PUT, "/" + id + "/points-scale", adminToken,
                Map.of("entries", List.of(Map.of("position", 1, "points", 10), Map.of("position", 2, "points", 8))));
        send(HttpMethod.DELETE, "/" + id + "/events/" + eventId, adminToken, null);
        send(HttpMethod.DELETE, "/" + id + "/classes/" + racingClassId, adminToken, null);

        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "CHAMPIONSHIP_CREATED", "CHAMPIONSHIP_UPDATED", "CHAMPIONSHIP_CLASS_ADDED", "CHAMPIONSHIP_EVENT_LINKED",
                "CHAMPIONSHIP_POINTS_SCALE_CHANGED", "CHAMPIONSHIP_EVENT_UNLINKED", "CHAMPIONSHIP_CLASS_REMOVED");
        assertThat(rows).allSatisfy(r -> assertThat(((Number) r.get("actor_user_id")).longValue()).isEqualTo(adminId));

        // The update says what changed: best 4 from 6 became 5 from 6
        assertThat(json(rows.get(1), "before_json").get("bestXFromYX").asInt()).isEqualTo(4);
        assertThat(json(rows.get(1), "after_json").get("bestXFromYX").asInt()).isEqualTo(5);
        // The scale before (none) and after
        assertThat(json(rows.get(4), "before_json")).isEmpty();
        assertThat(json(rows.get(4), "after_json")).hasSize(2);
        // What was removed is kept in the row
        assertThat(json(rows.get(5), "before_json").get("roundNumber").asInt()).isEqualTo(1);
        assertThat(rows.get(5).get("summary").toString()).contains("Round one " + run);
        assertThat(json(rows.get(6), "before_json").get("className").asText()).isEqualTo("Mod Buggy " + run);
    }

    @Test
    void aDisqualificationKeepsWhoMadeItAndWhyEvenAfterItIsRemoved() throws Exception {
        long id = create("DQ Championship " + run, 4, 6, 0, 0);

        // A referee records the DQ, an admin later removes it
        long exclusionId = send(HttpMethod.POST, "/" + id + "/exclusions", refereeToken,
                Map.of("driverId", competitorId, "eventId", eventId, "reason", "Used an illegal motor")).path("id").asLong();
        send(HttpMethod.DELETE, "/" + id + "/exclusions/" + exclusionId, adminToken, null);

        List<Map<String, Object>> rows = auditRows(id).stream()
                .filter(r -> r.get("action").toString().startsWith("CHAMPIONSHIP_EXCLUSION")).toList();
        assertThat(rows).extracting(r -> r.get("action"))
                .containsExactly("CHAMPIONSHIP_EXCLUSION_ADDED", "CHAMPIONSHIP_EXCLUSION_REMOVED");
        assertThat(((Number) rows.get(0).get("actor_user_id")).longValue()).isEqualTo(refereeId);
        assertThat(((Number) rows.get(1).get("actor_user_id")).longValue()).isEqualTo(adminId);
        assertThat(((Number) rows.get(1).get("event_id")).longValue()).isEqualTo(eventId);

        // The exclusion row is gone, but the removal row still says who excluded the driver, and why
        assertThat(jdbc.queryForObject("select count(*) from championship_exclusions where championship_id = ?",
                Integer.class, id)).isZero();
        JsonNode lost = json(rows.get(1), "before_json");
        assertThat(lost.get("reason").asText()).isEqualTo("Used an illegal motor");
        assertThat(lost.get("driverName").asText()).isEqualTo("Dan Driver " + run);
        assertThat(lost.get("recordedBy").asLong()).isEqualTo(refereeId);
        assertThat(lost.get("recordedByName").asText()).isEqualTo("Ray Referee");
        assertThat(rows.get(1).get("summary").toString()).contains("Dan Driver " + run);
    }

    @Test
    void aChangeThatIsRefusedLeavesNoRow() {
        long id = create("Refused Championship " + run, 4, 6, 0, 0);
        int before = auditRows(id).size();

        // The event is not linked, so there is nothing to unlink; and an unknown class cannot be added
        send(HttpMethod.DELETE, "/" + id + "/events/" + eventId, adminToken, null);
        ResponseEntity<String> refused = restTemplate.exchange("/api/v1/admin/championships/" + id + "/classes",
                HttpMethod.POST, new HttpEntity<>(Map.of("racingClassId", 987_654_321L), headers(adminToken)), String.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(auditRows(id)).hasSize(before);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long create(String name, int x, int y, int tq, int afinal) {
        Map<String, Object> body = Map.of("name", name, "bestXFromYX", x, "bestXFromYY", y,
                "tqBonusPoints", tq, "afinalWinnerBonusPoints", afinal);
        return send(HttpMethod.POST, "", adminToken, body).path("id").asLong();
    }

    private JsonNode send(HttpMethod method, String path, String token, Object body) {
        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/v1/admin/championships" + path, method,
                new HttpEntity<>(body, headers(token)), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("%s %s", method, path).isTrue();
        return response.getBody() == null ? objectMapper.createObjectNode() : response.getBody();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private List<Map<String, Object>> auditRows(long championshipId) {
        return jdbc.queryForList("select * from audit_log where entity_type = 'championship' and entity_id = ? order by id",
                String.valueOf(championshipId));
    }

    private JsonNode json(Map<String, Object> row, String column) throws Exception {
        Object value = row.get(column);
        return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value.toString());
    }

    private long createOfficial(String first, String last, Set<Role> roles) {
        User user = new User();
        user.setEmail("champ-audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName(first);
        user.setLastName(last);
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return user.getId();
    }

    private String login(long userId) {
        String email = userRepository.findById(userId).orElseThrow().getEmail();
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
