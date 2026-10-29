package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenService jwtTokenService;

    private static final String AUTH_BASE = "/api/v1/auth";
    private static final String ADMIN_PROFILE = "/api/v1/admin/club/profile";

    @Test
    void adminEndpoint_withTokenForAccountWithNoOfficialRole_returns403() {
        // Such accounts can no longer sign in; a token minted for one is still refused
        User user = createUserWithRoles("noroles-" + UUID.randomUUID() + "@test.com", "password123", Set.of());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTokenService.generateAccessToken(user));

        ResponseEntity<Void> response = restTemplate.exchange(
                ADMIN_PROFILE, HttpMethod.GET,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void otherAuthenticatedEndpoint_withTokenForAccountWithNoOfficialRole_returns403() {
        User user = createUserWithRoles("noroles-" + UUID.randomUUID() + "@test.com", "password123", Set.of());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTokenService.generateAccessToken(user));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/audio/voices", HttpMethod.GET,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminEndpoint_withAdminRole_returns200or404() {
        String email = "admin-" + UUID.randomUUID() + "@test.com";
        createUserWithRoles(email, "adminPass123", Set.of(Role.ADMIN));

        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(AUTH_BASE + "/login",
                new LoginRequest(email, "adminPass123"), AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResp.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                ADMIN_PROFILE, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value())
                .as("ADMIN user should get 200 (profile exists) or 404 (not created yet), but NOT 403")
                .isNotEqualTo(403);
    }

    @Test
    void adminEndpoint_withNoAuth_returns401or403() {
        ResponseEntity<Void> response = restTemplate.getForEntity(ADMIN_PROFILE, Void.class);

        assertThat(response.getStatusCode().value())
                .as("Unauthenticated request to admin endpoint should return 401 or 403")
                .isIn(401, 403);
    }

    @Test
    void stackableRoles_userWithMultipleRoles_canAccessAdminEndpoints() {
        String email = "multi-" + UUID.randomUUID() + "@test.com";
        createUserWithRoles(email, "multiPass123", Set.of(Role.ADMIN, Role.RACE_DIRECTOR));

        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(AUTH_BASE + "/login",
                new LoginRequest(email, "multiPass123"), AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse body = loginResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roles()).containsAnyOf("ADMIN", "RACE_DIRECTOR");

        String token = body.accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                ADMIN_PROFILE, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value())
                .as("User with ADMIN+RACE_DIRECTOR should access admin endpoints (200 or 404 acceptable, not 403)")
                .isNotEqualTo(403);
    }

    // --- helpers ---

    private User createUserWithRoles(String email, String password, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Test");
        user.setLastName("Admin");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }
}
