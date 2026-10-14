package dev.monkeypatch.rctiming.api.auth;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final String BASE_URL = "/api/v1/auth";

    /** Creates a user with a unique email and the given roles; returns the email. Password is "password123". */
    private String createUser(Set<Role> roles) {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return email;
    }

    @Test
    void login_validCredentials_returns200WithTokenAndCookie() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest(email, "password123"),
                AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();

        // Check Set-Cookie header contains HttpOnly refresh_token
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotEmpty();
        String cookieHeader = cookies.get(0);
        assertThat(cookieHeader).contains("refresh_token=");
        assertThat(cookieHeader).containsIgnoringCase("HttpOnly");
    }

    @Test
    void login_invalidPassword_returns401() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));

        ResponseEntity<Void> response = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest(email, "wrongPassword"),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_nonexistentEmail_returns401() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest("nobody@nowhere.com", "anypassword"),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_validCookie_returnsNewAccessToken() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest(email, "password123"),
                AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Extract the raw cookie value from Set-Cookie header
        List<String> setCookieHeaders = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotEmpty();
        // Find the refresh_token cookie (may be one of several Set-Cookie values)
        String refreshCookieHeader = setCookieHeaders.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No refresh_token cookie in: " + setCookieHeaders));
        String rawCookieValue = extractCookieValue(refreshCookieHeader, "refresh_token");
        assertThat(rawCookieValue).isNotBlank();

        // POST to refresh endpoint with the cookie in Cookie header
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + rawCookieValue);
        ResponseEntity<AuthResponse> refreshResponse = restTemplate.exchange(
                BASE_URL + "/refresh",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(headers),
                AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_accountWithNoOfficialRole_returns403() {
        String email = createUser(Set.of());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest(email, "password123"),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void login_noOfficialRoleAndWrongPassword_returns401() {
        // The 403 is only given once the password is right, so it reveals nothing new
        String email = createUser(Set.of());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                BASE_URL + "/login",
                new LoginRequest(email, "wrongPassword"),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_accountThatLostItsOfficialRoles_returns403() {
        String email = createUser(Set.of(Role.REFEREE));
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                BASE_URL + "/login", new LoginRequest(email, "password123"), AuthResponse.class);
        String rawCookieValue = extractCookieValue(loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow(), "refresh_token");
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRoles(Set.of());
        userRepository.save(user);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + rawCookieValue);
        ResponseEntity<Void> refreshResponse = restTemplate.exchange(
                BASE_URL + "/refresh",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(headers),
                Void.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerAndPasswordResetEndpoints_areGone() {
        for (String path : List.of("/register", "/password-reset/request", "/password-reset/confirm")) {
            ResponseEntity<Void> response = restTemplate.postForEntity(BASE_URL + path, "{}", Void.class);
            // No longer permitted anonymously and no longer mapped
            assertThat(response.getStatusCode().value()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void refresh_invalidCookie_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=garbage-invalid-token-value");
        RequestEntity<Void> request = RequestEntity
                .post(URI.create(BASE_URL + "/refresh"))
                .headers(headers)
                .build();

        ResponseEntity<Void> response = restTemplate.exchange(request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_revokesTheRefreshTokenAndClearsTheCookie() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));
        String cookie = loginCookie(email);

        ResponseEntity<Void> logout = deleteRefresh(cookie);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String cleared = logout.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst().orElseThrow();
        assertThat(cleared).startsWith("refresh_token=;");
        assertThat(cleared).contains("Max-Age=0").contains("Path=/api/v1/auth/refresh").containsIgnoringCase("HttpOnly");
        // The cookie the browser still holds can no longer sign anyone in
        assertThat(refresh(cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_withAnOlderCookie_alsoRevokesTheTokenItWasRotatedInto() {
        // The state a racing refresh leaves behind: the browser's cookie is one rotation behind
        String original = loginCookie(createUser(Set.of(Role.RACE_DIRECTOR)));
        ResponseEntity<Void> refreshed = refresh(original);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = extractCookieValue(refreshed.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow(), "refresh_token");
        assertThat(rotated).isNotEqualTo(original);

        deleteRefresh(original);

        assertThat(refresh(rotated).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_keepsTheTokenInTheSameFamily_soASeparateSignInIsUnaffectedByTheLogout() {
        String email = createUser(Set.of(Role.REFEREE));
        String first = loginCookie(email);
        String second = loginCookie(email);
        ResponseEntity<Void> refreshed = refresh(first);
        String rotatedFirst = extractCookieValue(refreshed.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow(), "refresh_token");

        deleteRefresh(rotatedFirst);

        assertThat(refresh(rotatedFirst).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logout_onlyEndsThisBrowsersSession() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));
        String thisBrowser = loginCookie(email);
        String otherBrowser = loginCookie(email);

        deleteRefresh(thisBrowser);

        assertThat(refresh(thisBrowser).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(otherBrowser).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refresh_theSameCookieUsedAtOnce_isAcceptedOnlyOnce() throws Exception {
        String cookie = loginCookie(createUser(Set.of(Role.RACE_DIRECTOR)));
        int callers = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<HttpStatus>> results = new java.util.ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return HttpStatus.valueOf(refresh(cookie).getStatusCode().value());
                }));
            }
            go.countDown();
            long accepted = 0;
            for (var result : results) {
                if (result.get(30, java.util.concurrent.TimeUnit.SECONDS) == HttpStatus.OK) accepted++;
            }

            assertThat(accepted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logout_withNoCookieOrAnUnknownOne_isStillNoContent() {
        assertThat(deleteRefresh(null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteRefresh("garbage-token").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_twice_isStillNoContent() {
        String cookie = loginCookie(createUser(Set.of(Role.REFEREE)));

        deleteRefresh(cookie);

        assertThat(deleteRefresh(cookie).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // --- the audit log (#138) ---

    @Test
    void aWrongPasswordIsRecordedAgainstTheOfficialItWasTriedOn() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR));
        Long officialId = userRepository.findByEmail(email).orElseThrow().getId();

        restTemplate.postForEntity(BASE_URL + "/login", new LoginRequest(email, "wrongPassword"), Void.class);

        var rows = jdbc.queryForList(
                "select action, entity_type, entity_id, summary, actor_user_id, actor_label, source from audit_log "
                        + "where action = 'LOGIN_FAILED' and entity_id = ?",
                String.valueOf(officialId));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "LOGIN_FAILED")
                .containsEntry("entity_type", "official")
                .containsEntry("entity_id", String.valueOf(officialId))
                .containsEntry("source", "UI");
        assertThat(rows.get(0).get("actor_user_id")).isNull();
        assertThat(rows.get(0)).containsEntry("actor_label", "anonymous:known account");
        assertThat((String) rows.get(0).get("summary")).doesNotContain("wrongPassword");
    }

    @Test
    void anUnknownEmailIsRecordedWithoutAnOfficial() {
        String email = "nobody-" + UUID.randomUUID() + "@example.com";

        restTemplate.postForEntity(BASE_URL + "/login", new LoginRequest(email, "whatever123"), Void.class);

        var rows = jdbc.queryForList(
                "select action, entity_id, actor_label, summary from audit_log "
                        + "where action = 'LOGIN_FAILED' and actor_label like 'anonymous:unknown email %' "
                        + "and actor_label not like ?",
                "%" + email + "%");
        assertThat(rows).isNotEmpty();
        var row = rows.get(rows.size() - 1);
        assertThat(row.get("entity_id")).isNull();
        assertThat(row.toString()).doesNotContain(email);
    }

    @Test
    void theSameUnknownEmailIsRecordedUnderTheSameLabelAndNeverAsTyped() {
        String email = "Nobody-" + UUID.randomUUID() + "@Example.com";

        String latest = "select actor_label from audit_log where action = 'LOGIN_FAILED' order by id desc limit 1";
        restTemplate.postForEntity(BASE_URL + "/login", new LoginRequest(email, "whatever123"), Void.class);
        String first = jdbc.queryForObject(latest, String.class);
        restTemplate.postForEntity(BASE_URL + "/login", new LoginRequest(email.toLowerCase(), "whatever123"),
                Void.class);
        String second = jdbc.queryForObject(latest, String.class);

        assertThat(first).startsWith("anonymous:unknown email ").isEqualTo(second);
        assertThat(first).doesNotContain(email.toLowerCase());
    }

    @Test
    void anAccountWithNoOfficialRoleIsRecordedAsRefused() {
        String email = createUser(Set.of());

        restTemplate.postForEntity(BASE_URL + "/login", new LoginRequest(email, "password123"), Void.class);

        Long officialId = userRepository.findByEmail(email).orElseThrow().getId();

        String summary = jdbc.queryForObject(
                "select summary from audit_log where action = 'LOGIN_FAILED' and entity_id = ?", String.class,
                String.valueOf(officialId));
        assertThat(summary).isEqualTo("Sign-in refused: not an official");
    }

    @Test
    void aSignInIsRecordedAgainstTheOfficial() {
        String email = createUser(Set.of(Role.REFEREE));
        Long officialId = userRepository.findByEmail(email).orElseThrow().getId();

        loginCookie(email);

        Integer count = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'LOGIN_SUCCEEDED' and actor_user_id = ?",
                Integer.class, officialId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void aSignOutIsRecordedOnceAndASecondOneAddsNothing() {
        String email = createUser(Set.of(Role.REFEREE));
        Long officialId = userRepository.findByEmail(email).orElseThrow().getId();
        String cookie = loginCookie(email);

        deleteRefresh(cookie);
        deleteRefresh(cookie);

        Integer count = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'LOGOUT' and actor_user_id = ?", Integer.class, officialId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void aRefusedRefreshIsRecordedButNoCookieAtAllIsNot() {
        String email = createUser(Set.of(Role.REFEREE));
        Long officialId = userRepository.findByEmail(email).orElseThrow().getId();
        String cookie = loginCookie(email);
        deleteRefresh(cookie);
        int before = refusedRefreshRows(officialId);

        refresh(cookie);                       // a token that has been signed out
        restTemplate.postForEntity(BASE_URL + "/refresh", null, Void.class);   // no cookie: an anonymous page load

        assertThat(refusedRefreshRows(officialId)).isEqualTo(before + 1);
        String summary = jdbc.queryForObject(
                "select summary from audit_log where action = 'REFRESH_REFUSED' and entity_id = ?", String.class,
                String.valueOf(officialId));
        assertThat(summary).isEqualTo("Refresh refused: token already used or revoked");
    }

    private int refusedRefreshRows(Long officialId) {
        return jdbc.queryForObject(
                "select count(*) from audit_log where action = 'REFRESH_REFUSED' and entity_id = ?", Integer.class,
                String.valueOf(officialId));
    }

    // --- helpers ---

    /** Signs in and returns the raw refresh cookie value, as a browser would hold it. */
    private String loginCookie(String email) {
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                BASE_URL + "/login", new LoginRequest(email, "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String header = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst().orElseThrow();
        return extractCookieValue(header, "refresh_token");
    }

    private ResponseEntity<Void> deleteRefresh(String cookieValue) {
        HttpHeaders headers = new HttpHeaders();
        if (cookieValue != null) {
            headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        }
        return restTemplate.exchange(RequestEntity.delete(URI.create(BASE_URL + "/refresh")).headers(headers).build(),
                Void.class);
    }

    private ResponseEntity<Void> refresh(String cookieValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        return restTemplate.exchange(RequestEntity.post(URI.create(BASE_URL + "/refresh")).headers(headers).build(),
                Void.class);
    }

    private String extractCookieValue(String setCookieHeader, String cookieName) {
        for (String part : setCookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith(cookieName + "=")) {
                return part.substring((cookieName + "=").length());
            }
        }
        return null;
    }
}
