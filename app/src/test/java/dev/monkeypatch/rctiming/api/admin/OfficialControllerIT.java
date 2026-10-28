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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Officials page's API (#61), end to end through sign-in and refresh. */
@SuppressWarnings("unchecked")
class OfficialControllerIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/officials";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @LocalServerPort
    int port;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    private long adminId;
    private String adminName;
    private String adminToken;

    @BeforeEach
    void signInAsAdmin() {
        String email = createUser(Set.of(Role.ADMIN), "adminPass123");
        User admin = userRepository.findByEmail(email).orElseThrow();
        adminId = admin.getId();
        adminName = admin.getFirstName() + " " + admin.getLastName();
        adminToken = login(email, "adminPass123").getBody().accessToken();
    }

    @Test
    void anAdminAddsAnOfficialWhoCanThenSignIn() {
        String email = "new-official-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map<String, Object>> added = rest.exchange(BASE, HttpMethod.POST, json(Map.of(
                "email", email, "firstName", "Rita", "lastName", "Referee",
                "password", "startPass1", "roles", List.of("REFEREE", "RACE_DIRECTOR"))), MAP);

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(added.getBody()).containsEntry("email", email).containsEntry("enabled", true);
        assertThat((List<Object>) added.getBody().get("roles")).containsExactly("RACE_DIRECTOR", "REFEREE");
        assertThat(login(email, "startPass1").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<Map<String, Object>>> list = rest.exchange(BASE, HttpMethod.GET, auth(), LIST);
        assertThat(list.getBody()).anySatisfy(o -> assertThat(o).containsEntry("email", email));

        assertThat(changesFor(email)).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("action", "ADDED").containsEntry("actorName", adminName);
            assertThat((String) change.get("detail")).isEqualTo("Roles: RACE_DIRECTOR, REFEREE");
        });
    }

    @Test
    void changesComePageByPageNewestFirstWithNoneSkippedOrRepeated() {
        String email = createUser(Set.of(Role.REFEREE), "password123");
        long id = idOf(email);
        rest.exchange(BASE + "/" + id + "/roles", HttpMethod.PUT, json(Map.of("roles", List.of("RACE_DIRECTOR"))), MAP);
        rest.exchange(BASE + "/" + id + "/roles", HttpMethod.PUT, json(Map.of("roles", List.of("REFEREE"))), MAP);
        rest.exchange(BASE + "/" + id + "/password", HttpMethod.PUT, json(Map.of("password", "newPassword1")),
                Void.class);

        List<Map<String, Object>> first = rest.exchange(BASE + "/changes?size=2", HttpMethod.GET, auth(), LIST)
                .getBody();
        long oldestShown = ((Number) first.get(first.size() - 1).get("id")).longValue();
        List<Map<String, Object>> next = rest.exchange(BASE + "/changes?size=2&before=" + oldestShown,
                HttpMethod.GET, auth(), LIST).getBody();

        assertThat(first).hasSize(2);
        assertThat(first.get(0)).containsEntry("action", "PASSWORD_SET");
        assertThat(first.get(1)).containsEntry("action", "ROLES_CHANGED");
        assertThat(next).isNotEmpty().hasSizeLessThanOrEqualTo(2);
        assertThat(next).allSatisfy(c -> assertThat(((Number) c.get("id")).longValue()).isLessThan(oldestShown));
        assertThat(next.get(0)).containsEntry("action", "ROLES_CHANGED")
                .containsEntry("detail", "REFEREE → RACE_DIRECTOR");
    }

    @Test
    void changesPageSizeIsClampedAndABadCursorFindsNothing() {
        assertThat(rest.exchange(BASE + "/changes?size=0", HttpMethod.GET, auth(), LIST).getBody())
                .hasSizeLessThanOrEqualTo(1);
        assertThat(rest.exchange(BASE + "/changes?size=100000", HttpMethod.GET, auth(), LIST).getBody())
                .hasSizeLessThanOrEqualTo(OfficialController.CHANGES_PAGE_MAX);
        assertThat(rest.exchange(BASE + "/changes?before=0", HttpMethod.GET, auth(), LIST).getBody()).isEmpty();
        assertThat(rest.exchange(BASE + "/changes?before=abc", HttpMethod.GET, auth(), MAP).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anEmailAlreadyInUseIsRefused() {
        String email = createUser(Set.of(Role.REFEREE), "password123");

        ResponseEntity<Map<String, Object>> response = rest.exchange(BASE, HttpMethod.POST, json(Map.of(
                "email", email, "firstName", "Dup", "lastName", "Licate",
                "password", "password123", "roles", List.of("REFEREE"))), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aShortPasswordOrNoRolesIsRefused() {
        Map<String, Object> shortPassword = Map.of("email", "short-" + UUID.randomUUID() + "@example.com",
                "firstName", "A", "lastName", "B", "password", "short", "roles", List.of("REFEREE"));
        Map<String, Object> noRoles = Map.of("email", "none-" + UUID.randomUUID() + "@example.com",
                "firstName", "A", "lastName", "B", "password", "longEnough1", "roles", List.of());

        assertThat(rest.exchange(BASE, HttpMethod.POST, json(shortPassword), MAP).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.exchange(BASE, HttpMethod.POST, json(noRoles), MAP).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAdminChangesAnOfficialsRoles() {
        String email = createUser(Set.of(Role.REFEREE), "password123");
        long id = idOf(email);

        ResponseEntity<Map<String, Object>> changed = rest.exchange(BASE + "/" + id + "/roles", HttpMethod.PUT,
                json(Map.of("roles", List.of("RACE_DIRECTOR"))), MAP);

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) changed.getBody().get("roles")).containsExactly("RACE_DIRECTOR");
        assertThat(userRepository.findById(id).orElseThrow().getRoles()).containsExactly(Role.RACE_DIRECTOR);
        assertThat(changesFor(email)).singleElement().satisfies(change ->
                assertThat(change).containsEntry("action", "ROLES_CHANGED")
                        .containsEntry("detail", "REFEREE → RACE_DIRECTOR")
                        .containsEntry("actorName", adminName));
    }

    @Test
    void settingAPasswordReplacesTheOldOneAndEndsSessions() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR), "oldPassword1");
        String refreshCookie = refreshCookie(login(email, "oldPassword1"));

        ResponseEntity<Void> set = rest.exchange(BASE + "/" + idOf(email) + "/password", HttpMethod.PUT,
                json(Map.of("password", "newPassword1")), Void.class);

        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(email, "oldPassword1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "newPassword1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh(refreshCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(changesFor(email)).singleElement().satisfies(change ->
                assertThat(change).containsEntry("action", "PASSWORD_SET").containsEntry("detail", null));
    }

    @Test
    void aDisabledOfficialCannotSignInOrRefreshUntilEnabledAgain() {
        String email = createUser(Set.of(Role.REFEREE), "password123");
        long id = idOf(email);
        String refreshCookie = refreshCookie(login(email, "password123"));

        ResponseEntity<Map<String, Object>> disabled = rest.exchange(BASE + "/" + id + "/disable", HttpMethod.POST, auth(), MAP);

        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody()).containsEntry("enabled", false);
        ResponseEntity<Map<String, Object>> signIn = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, "password123")), MAP);
        assertThat(signIn.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(signIn.getBody()).containsEntry("reason", "disabled");
        assertThat(refresh(refreshCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map<String, Object>> enabled = rest.exchange(BASE + "/" + id + "/enable", HttpMethod.POST, auth(), MAP);

        assertThat(enabled.getBody()).containsEntry("enabled", true);
        assertThat(login(email, "password123").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changesFor(email)).extracting(change -> change.get("action")).containsExactly("ENABLED", "DISABLED");
    }

    @Test
    void disablingAnOfficialClosesTheirLiveTimingSocket() throws Exception {
        String email = createUser(Set.of(Role.RACE_DIRECTOR), "password123");
        String token = login(email, "password123").getBody().accessToken();
        StompSession socket = connectStomp(token);

        rest.exchange(BASE + "/" + idOf(email) + "/disable", HttpMethod.POST, auth(), MAP);

        awaitClosed(socket);
        assertThatThrownBy(() -> connectStomp(token)).isInstanceOf(Exception.class);
    }

    @Test
    void settingAPasswordClosesTheirLiveTimingSocketAndRefusesTheOldToken() throws Exception {
        String email = createUser(Set.of(Role.REFEREE), "password123");
        String oldToken = login(email, "password123").getBody().accessToken();
        StompSession socket = connectStomp(oldToken);

        rest.exchange(BASE + "/" + idOf(email) + "/password", HttpMethod.PUT,
                json(Map.of("password", "newPassword456")), Void.class);

        awaitClosed(socket);
        assertThatThrownBy(() -> connectStomp(oldToken)).isInstanceOf(Exception.class);
        // A token's issued-at is to the second, and one from the second of the change is refused too
        Thread.sleep(1_100);
        StompSession signedInAgain = connectStomp(login(email, "newPassword456").getBody().accessToken());
        assertThat(signedInAgain.isConnected()).isTrue();
        signedInAgain.disconnect();
    }

    @Test
    void anAdminCannotDisableThemselves() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(BASE + "/" + adminId + "/disable", HttpMethod.POST, auth(), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(adminId).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void onlyAdminsManageOfficials() {
        String email = createUser(Set.of(Role.RACE_DIRECTOR, Role.REFEREE), "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login(email, "password123").getBody().accessToken());

        ResponseEntity<String> response = rest.exchange(BASE, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownOfficialIsNotFound() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(BASE + "/999999/disable", HttpMethod.POST, auth(), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @AfterEach
    void stopStompClient() {
        stompClient.stop();
    }

    // --- helpers ---

    private StompSession connectStomp(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws/timing", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() {}).get(3, TimeUnit.SECONDS);
    }

    private static void awaitClosed(StompSession socket) throws InterruptedException {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(socket.isConnected()).as("socket closed").isFalse());
    }

    private String createUser(Set<Role> roles, String password) {
        String email = "official-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Test");
        user.setLastName("Official");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        return email;
    }

    private long idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private ResponseEntity<AuthResponse> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    }

    private static String refreshCookie(ResponseEntity<?> login) {
        return login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow();
    }

    private ResponseEntity<String> refresh(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    /** Changes to one official, newest first. */
    private List<Map<String, Object>> changesFor(String email) {
        long id = idOf(email);
        return rest.exchange(BASE + "/changes", HttpMethod.GET, auth(), LIST).getBody().stream()
                .filter(change -> ((Number) change.get("officialId")).longValue() == id)
                .toList();
    }

    private HttpEntity<Void> auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return new HttpEntity<>(body, headers);
    }
}
