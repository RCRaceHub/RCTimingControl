package dev.monkeypatch.rctiming.domain.audit;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.query.audit.AuditEntryDto;
import dev.monkeypatch.rctiming.query.audit.AuditFilter;
import dev.monkeypatch.rctiming.query.audit.AuditPageDto;
import dev.monkeypatch.rctiming.query.audit.AuditQueryService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogIT extends AbstractIntegrationTest {

    @Autowired AuditService audit;
    @Autowired AuditQueryService query;
    @Autowired TransactionTemplate transactions;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbc;

    /** Users this test created, removed afterwards: every test class shares one database. */
    private final List<Long> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : createdUsers) {
            jdbc.update("delete from audit_log where actor_user_id = ?", id);
            jdbc.update("delete from refresh_tokens where user_id = ?", id);
            jdbc.update("delete from user_roles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
        jdbc.update("delete from audit_log where entity_type like 'it-%'");
        createdUsers.clear();
    }

    /** Every test works on its own entity type, because all tests share one database. */
    private static String uniqueType() {
        return "it-" + UUID.randomUUID();
    }

    private AuditPageDto find(String entityType) {
        return query.search(new AuditFilter(entityType, null, null, null, null, null, null, null), 0, 50);
    }

    @Test
    void aChangeRecordsItsAuditRowInTheSameTransaction() {
        String type = uniqueType();

        transactions.executeWithoutResult(status ->
                audit.entry(Actor.system("test"), "THING_CHANGED").entity(type, 7).event(3L).race(4L)
                        .summary("Changed the thing").record());

        AuditEntryDto row = find(type).entries().get(0);
        assertThat(row.action()).isEqualTo("THING_CHANGED");
        assertThat(row.entityId()).isEqualTo("7");
        assertThat(row.eventId()).isEqualTo(3L);
        assertThat(row.raceId()).isEqualTo(4L);
        assertThat(row.actor()).isEqualTo("system:test");
        assertThat(row.source()).isEqualTo("SYSTEM");
        assertThat(row.summary()).isEqualTo("Changed the thing");
        assertThat(row.at()).isNotNull();
    }

    @Test
    void theRowIsRolledBackWithTheChange() {
        String type = uniqueType();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            audit.entry(Actor.system("test"), "THING_CHANGED").entity(type, 1).summary("Changed").record();
            throw new IllegalStateException("the change itself failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(find(type).entries()).isEmpty();
    }

    @Test
    void recordRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> audit.entry(Actor.system("test"), "THING_CHANGED").summary("Changed").record())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aStandaloneRowIsWrittenOnItsOwn() {
        String type = uniqueType();

        audit.entry(Actor.anonymous("unknown email"), "LOGIN_FAILED").entity(type, null)
                .summary("Sign-in refused: wrong email or password").recordStandalone();

        AuditEntryDto row = find(type).entries().get(0);
        assertThat(row.actor()).isEqualTo("anonymous:unknown email");
        assertThat(row.actorUserId()).isNull();
        assertThat(row.entityId()).isNull();
    }

    @Test
    void anOfficialIsNamedByNameAndEmailAtTheTime() {
        String type = uniqueType();
        User official = official();

        audit.entry(Actor.official(official.getId()), "THING_CHANGED").entity(type, 1).summary("Changed")
                .recordStandalone();

        AuditEntryDto row = find(type).entries().get(0);
        assertThat(row.actorUserId()).isEqualTo(official.getId());
        assertThat(row.actor()).isEqualTo("Audit Tester <" + official.getEmail() + ">");
        assertThat(row.source()).isEqualTo("UI");
    }

    @Test
    void beforeAndAfterAreStoredAsJson() {
        String type = uniqueType();

        audit.entry(Actor.system("test"), "STATUS_CHANGED").entity(type, 1).summary("Changed status")
                .before(Map.of("status", "ACTIVE")).after(Map.of("status", "WITHDRAWN", "laps", 3))
                .recordStandalone();

        AuditEntryDto row = find(type).entries().get(0);
        assertThat(row.before().get("status").asString()).isEqualTo("ACTIVE");
        JsonNode after = row.after();
        assertThat(after.get("status").asString()).isEqualTo("WITHDRAWN");
        assertThat(after.get("laps").asInt()).isEqualTo(3);
    }

    @Test
    void aRowNeedsASummary() {
        assertThatThrownBy(() -> audit.entry(Actor.system("test"), "THING_CHANGED").recordStandalone())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchFiltersAndPagesNewestFirst() {
        String type = uniqueType();
        for (int i = 1; i <= 3; i++) {
            audit.entry(Actor.system("test"), i == 2 ? "OTHER" : "THING_CHANGED").entity(type, i)
                    .summary("Row " + i).recordStandalone();
        }

        AuditPageDto firstPage = query.search(
                new AuditFilter(type, null, null, null, null, null, null, null), 0, 2);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.entries()).extracting(AuditEntryDto::summary).containsExactly("Row 3", "Row 2");
        AuditPageDto secondPage = query.search(
                new AuditFilter(type, null, null, null, null, null, null, null), 1, 2);
        assertThat(secondPage.entries()).extracting(AuditEntryDto::summary).containsExactly("Row 1");

        AuditPageDto byAction = query.search(
                new AuditFilter(type, null, "OTHER", null, null, null, null, null), 0, 50);
        assertThat(byAction.entries()).extracting(AuditEntryDto::summary).containsExactly("Row 2");

        AuditPageDto byEntity = query.search(
                new AuditFilter(type, "3", null, null, null, null, null, null), 0, 50);
        assertThat(byEntity.entries()).extracting(AuditEntryDto::summary).containsExactly("Row 3");

        AuditPageDto future = query.search(
                new AuditFilter(type, null, null, null, null, null, Instant.now().plusSeconds(3600), null), 0, 50);
        assertThat(future.entries()).isEmpty();
    }

    @Test
    void anAdminCanReadTheLogAndOtherOfficialsCannot() {
        String type = uniqueType();
        audit.entry(Actor.system("test"), "THING_CHANGED").entity(type, 1).summary("Changed").recordStandalone();
        String url = "/api/v1/admin/audit?entityType=" + type;

        ResponseEntity<JsonNode> asAdmin = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(loginAs(Set.of(Role.ADMIN)))), JsonNode.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asAdmin.getBody().get("total").asLong()).isEqualTo(1);
        assertThat(asAdmin.getBody().get("entries").get(0).get("summary").asString()).isEqualTo("Changed");

        ResponseEntity<JsonNode> asReferee = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(loginAs(Set.of(Role.REFEREE)))), JsonNode.class);
        assertThat(asReferee.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> anonymous = restTemplate.getForEntity(url, JsonNode.class);
        assertThat(anonymous.getStatusCode().value()).isIn(401, 403);
    }

    private User official() {
        return official(Set.of(Role.RACE_DIRECTOR));
    }

    private User official(Set<Role> roles) {
        User user = new User();
        user.setEmail("audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Audit");
        user.setLastName("Tester");
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        createdUsers.add(saved.getId());
        return saved;
    }

    private String loginAs(Set<Role> roles) {
        User user = official(roles);
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
