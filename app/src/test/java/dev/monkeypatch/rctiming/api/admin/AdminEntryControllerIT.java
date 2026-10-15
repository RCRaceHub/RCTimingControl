package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;

class AdminEntryControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenService jwtTokenService;

    private String adminToken;
    private Long adminUserId;

    private static final long OPEN_EVENT_ID = 1001L;
    private static final long OPEN_CLASS_ID = 2001L;

    @BeforeEach
    void setUp() {
        String email = "admin-entry-" + UUID.randomUUID() + "@test.com";
        adminUserId = createAdminUser(email, "adminPass123", Set.of(Role.ADMIN));
        var loginResp = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"), AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listEntriesForClass_returnsWalkInEntry() {
        String name = "List Entries " + UUID.randomUUID();
        String transponder = uniqueNumber();
        Long entryId = createWalkIn(name, transponder);

        var resp = restTemplate.exchange(
                "/api/v1/admin/entries/events/" + OPEN_EVENT_ID + "/classes/" + OPEN_CLASS_ID,
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> ourEntry = ((List<Map<String, Object>>) resp.getBody()).stream()
                .filter(e -> entryId.equals(((Number) e.get("id")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry not in list: " + entryId));
        assertThat(ourEntry.get("transponderNumber")).isEqualTo(transponder);
        assertThat(ourEntry.get("displayName")).isEqualTo(name);
    }

    @Test
    void adminWithdraw_setsStatusAndWritesAudit() {
        Long entryId = createWalkIn("Withdraw Me " + UUID.randomUUID(), uniqueNumber());

        var withdrawBody = Map.of("reason", "admin test withdrawal");
        var withdrawResp = restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/withdraw",
                HttpMethod.POST, new HttpEntity<>(withdrawBody, adminHeaders()), Map.class);

        assertThat(withdrawResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawResp.getBody().get("status")).isEqualTo("WITHDRAWN");

        List<EntryAuditLogRecord> logs = entryLog(entryId);
        assertThat(logs).anyMatch(l -> "ADMIN_WITHDRAW".equals(l.getAction()));
    }

    @Test
    void adminWithdraw_blankReason_returns400() {
        var resp = restTemplate.exchange("/api/v1/admin/entries/1/withdraw",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", ""), adminHeaders()),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refereeCannotWithdraw() {
        Long entryId = createWalkIn("Referee Withdraw " + UUID.randomUUID(), uniqueNumber());
        String refereeToken = tokenFor(Set.of(Role.REFEREE));

        var resp = restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/withdraw",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "no"), headersFor(refereeToken)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void history_listsTheWalkInAndTheWithdrawalWithTheirReasonsOldestFirst() {
        Long entryId = createWalkIn("History Hannah " + UUID.randomUUID(), uniqueNumber());
        restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/withdraw",
                HttpMethod.POST, new HttpEntity<>(Map.of("reason", "Car broke"), adminHeaders()), Map.class);

        var resp = restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/history",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).extracting(r -> r.get("summary"))
                .containsExactly("Added by hand as a walk-in", "Withdrawn");
        assertThat(resp.getBody().get(1).get("reason")).isEqualTo("Car broke");
        assertThat(resp.getBody().get(1).get("actor")).isNotNull();
    }

    @Test
    void history_putsTheTransponderSwapBetweenTheWalkInAndTheWithdrawal() {
        String oldNumber = uniqueNumber();
        String newNumber = uniqueNumber();
        Long entryId = createWalkIn("History Swap " + UUID.randomUUID(), oldNumber);
        assertThat(restTemplate.exchange(
                "/api/v1/race-control/events/" + OPEN_EVENT_ID + "/entries/" + entryId + "/transponder-swap",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("slot", "PRIMARY", "newTransponderNumber", newNumber), adminHeaders()),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        var resp = restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/history",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getBody()).extracting(r -> r.get("summary").toString()).hasSize(2);
        assertThat(resp.getBody().get(0).get("summary")).isEqualTo("Added by hand as a walk-in");
        assertThat(resp.getBody().get(1).get("summary"))
                .isEqualTo("Changed the primary transponder from " + oldNumber + " to " + newNumber);
        // The audit log keeps the sign-in label with the email; the history shows the name only
        assertThat(resp.getBody().get(1).get("actor").toString()).doesNotContain("@");
    }

    @Test
    void history_isForAdminsOnly() {
        Long entryId = createWalkIn("History Private " + UUID.randomUUID(), uniqueNumber());

        for (Role role : new Role[] {Role.RACE_DIRECTOR, Role.REFEREE}) {
            var resp = restTemplate.exchange("/api/v1/admin/entries/" + entryId + "/history",
                    HttpMethod.GET, new HttpEntity<>(headersFor(tokenFor(Set.of(role)))), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void history_ofAnUnknownEntryIsNotFound() {
        var resp = restTemplate.exchange("/api/v1/admin/entries/999999999/history",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void accountWithNoOfficialRole_cannotListEntries() {
        var resp = restTemplate.exchange(
                "/api/v1/admin/entries/events/" + OPEN_EVENT_ID + "/classes/" + OPEN_CLASS_ID,
                HttpMethod.GET, new HttpEntity<>(headersFor(tokenFor(Set.of()))), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void racerTransponderSwapAndMembershipOverrideEndpoints_areGone() {
        var swap = restTemplate.exchange("/api/v1/admin/entries/1/transponder", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("transponderId", 1), adminHeaders()), String.class);
        var override = restTemplate.exchange("/api/v1/admin/entries/1/membership-override", HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "x"), adminHeaders()), String.class);

        assertThat(swap.getStatusCode().value()).isIn(404, 405);
        assertThat(override.getStatusCode().value()).isIn(404, 405);
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private Long createWalkIn(String name, String transponder) {
        var resp = restTemplate.exchange("/api/v1/admin/entries", HttpMethod.POST,
                new HttpEntity<>(Map.of("eventId", OPEN_EVENT_ID, "eventClassId", OPEN_CLASS_ID,
                        "competitorName", name, "primaryTransponder", transponder), adminHeaders()),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) ((Map<String, Object>) resp.getBody().get("entry")).get("id")).longValue();
    }

    /** A token for a new user with the given roles, minted directly (no-role accounts cannot sign in). */
    private String tokenFor(Set<Role> roles) {
        Long id = createAdminUser("entry-staff-" + UUID.randomUUID() + "@test.com", "password123", roles);
        return jwtTokenService.generateAccessToken(userRepository.findById(id).orElseThrow());
    }

    private Long createAdminUser(String email, String password, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getId();
    }

    private HttpHeaders adminHeaders() {
        return headersFor(adminToken);
    }

    private HttpHeaders headersFor(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static int counter = 0;

    /** Transponder numbers are at most 20 characters. */
    private String uniqueNumber() {
        return "A" + (System.nanoTime() % 1_000_000_000_000L) + (++counter);
    }

    private List<EntryAuditLogRecord> entryLog(Long entryId) {
        return dsl.selectFrom(ENTRY_AUDIT_LOG)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(entryId))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT, ENTRY_AUDIT_LOG.ID)
                .fetch();
    }
}
