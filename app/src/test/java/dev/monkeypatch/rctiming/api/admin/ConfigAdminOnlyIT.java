package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Club config, tracks, racing classes, formats, event classes, events and championships can be changed by
 * an admin only (#132). Any official can still read them, a race director can still move an event
 * through its day (publish, open, close entries, start, complete), and any official can still record or
 * remove a championship exclusion, because a referee's disqualification (DQ) goes there.
 *
 * <p>The write attempts here either have no body or a valid one, because a body that fails validation is
 * answered 400 before the role check runs. The ids do not exist, so an admin gets 404, which shows the 403
 * for the others is the role and not a missing record.
 */
class ConfigAdminOnlyIT extends AbstractIntegrationTest {

    private static final long MISSING = 987_654_321L;

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // The database is shared by every integration test, and others pick a user with findAll(), so the
    // accounts made here are removed again
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void removeCreatedUsers() {
        createdUserIds.forEach(userRepository::deleteById);
        createdUserIds.clear();
    }

    @Test
    void anyOfficialCanStillReadTheConfig() {
        for (Role role : List.of(Role.RACE_DIRECTOR, Role.REFEREE)) {
            String token = loginAs(role);
            for (String path : List.of("/api/v1/admin/tracks", "/api/v1/admin/classes", "/api/v1/admin/formats",
                    "/api/v1/admin/events", "/api/v1/admin/championships", "/api/v1/admin/club/affiliations")) {
                assertThat(call(HttpMethod.GET, path, token, null).getStatusCode())
                        .as("%s reading %s", role, path).isEqualTo(HttpStatus.OK);
            }
        }
    }

    @Test
    void raceDirectorsAndRefereesCannotChangeTheConfig() {
        Map<String, HttpMethod> changes = new java.util.LinkedHashMap<>();
        changes.put("/api/v1/admin/tracks/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/tracks/loops/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/tracks/thresholds/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/classes/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/formats/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/club/affiliations/" + MISSING, HttpMethod.DELETE);
        changes.put("/api/v1/admin/championships/" + MISSING + "/classes/1", HttpMethod.DELETE);
        changes.put("/api/v1/admin/championships/" + MISSING + "/events/1", HttpMethod.DELETE);

        for (Role role : List.of(Role.RACE_DIRECTOR, Role.REFEREE)) {
            String token = loginAs(role);
            changes.forEach((path, method) -> assertThat(call(method, path, token, null).getStatusCode())
                    .as("%s %s as %s", method, path, role).isEqualTo(HttpStatus.FORBIDDEN));
            assertThat(call(HttpMethod.POST, "/api/v1/admin/classes", token, Map.of("name", "Blocked " + UUID.randomUUID()))
                    .getStatusCode()).as("%s creating a class", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void anyOfficialCanStillRecordOrRemoveAnExclusion_aRefereesDisqualification() {
        for (Role role : List.of(Role.RACE_DIRECTOR, Role.REFEREE)) {
            // Past the role check: the championship does not exist, so 404 rather than 403
            assertThat(call(HttpMethod.DELETE, "/api/v1/admin/championships/" + MISSING + "/exclusions/1",
                    loginAs(role), null).getStatusCode()).as("%s removing an exclusion", role)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void anAdminGetsPastTheRoleCheckOnTheSameRequests() {
        String token = loginAs(Role.ADMIN);

        for (String path : List.of("/api/v1/admin/tracks/" + MISSING, "/api/v1/admin/classes/" + MISSING,
                "/api/v1/admin/formats/" + MISSING)) {
            assertThat(call(HttpMethod.DELETE, path, token, null).getStatusCode())
                    .as("admin deleting %s", path).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void aRaceDirectorCanMoveAnEventThroughItsDayButARefereeCannot() {
        Map<String, String> toPublished = Map.of("targetStatus", "PUBLISHED");
        String path = "/api/v1/admin/events/" + MISSING + "/transition";

        assertThat(call(HttpMethod.POST, path, loginAs(Role.REFEREE), toPublished).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Past the role check: the event does not exist, so 404 rather than 403
        assertThat(call(HttpMethod.POST, path, loginAs(Role.RACE_DIRECTOR), toPublished).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String loginAs(Role role) {
        User user = new User();
        user.setEmail("config-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Config");
        user.setLastName(role.name());
        user.setRoles(Set.of(role));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        createdUserIds.add(user.getId());
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
