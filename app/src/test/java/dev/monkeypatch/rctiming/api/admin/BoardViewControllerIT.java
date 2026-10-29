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

import static org.assertj.core.api.Assertions.assertThat;

/** The saved board views (#481): the seeded views, the anonymous read, create, change, delete, validation and who may change them. */
@SuppressWarnings({"rawtypes", "unchecked"})
class BoardViewControllerIT extends AbstractIntegrationTest {

    private static final String ADMIN_URL = "/api/v1/admin/board-views";
    private static final String PUBLIC_URL = "/api/v1/boards/views/";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String directorToken;
    private String refereeToken;
    private String slug;

    @BeforeEach
    void setUp() {
        slug = "it-" + UUID.randomUUID().toString().substring(0, 8);
        directorToken = login(Role.RACE_DIRECTOR);
        refereeToken = login(Role.REFEREE);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from board_views where slug like 'it-%'");
    }

    private Map<String, Object> body(String slugValue, String name) {
        return Map.of("slug", slugValue, "name", name, "columns", 2,
                "panels", List.of(Map.of("type", "LIVE_TIMING", "span", 2), Map.of("type", "LIVE_TOP", "span", 1)));
    }

    @Test
    void theSeededViewsCanBeReadWithoutSigningIn() {
        ResponseEntity<Map> commentator = restTemplate.getForEntity(PUBLIC_URL + "commentator", Map.class);

        assertThat(commentator.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commentator.getBody().get("columns")).isEqualTo(2);
        assertThat((List<?>) commentator.getBody().get("panels")).hasSize(3);
        assertThat(restTemplate.getForEntity(PUBLIC_URL + "monitor", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity(PUBLIC_URL + "no-such-view", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRaceDirectorCreatesChangesAndDeletesAViewAndTheLiveTopPanelGetsTenPlaces() {
        ResponseEntity<Map> created = send(HttpMethod.POST, ADMIN_URL, body(slug, "Pit"), directorToken);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = ((Number) created.getBody().get("id")).longValue();
        List<Map<String, Object>> panels = (List<Map<String, Object>>) created.getBody().get("panels");
        assertThat(panels.get(1).get("top")).isEqualTo(10);

        assertThat(restTemplate.getForEntity(PUBLIC_URL + slug, Map.class).getBody().get("name")).isEqualTo("Pit");

        ResponseEntity<Map> changed = send(HttpMethod.PUT, ADMIN_URL + "/" + id, body(slug, "Pit monitor"), directorToken);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("name")).isEqualTo("Pit monitor");

        assertThat(send(HttpMethod.DELETE, ADMIN_URL + "/" + id, null, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(PUBLIC_URL + slug, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aTakenAddressNameIsAConflictAndABadLayoutIsRefused() {
        assertThat(send(HttpMethod.POST, ADMIN_URL, body(slug, "One"), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(send(HttpMethod.POST, ADMIN_URL, body(slug, "Two"), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.POST, ADMIN_URL, body("Bad Slug!", "Bad"), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, ADMIN_URL, Map.of("slug", slug + "-b", "name", "No panels", "columns", 2,
                "panels", List.of()), directorToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRefereeCanReadTheListButNotChangeAView() {
        ResponseEntity<List> list = restTemplate.exchange(ADMIN_URL, HttpMethod.GET,
                new HttpEntity<>(headers(refereeToken)), List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.POST, ADMIN_URL, body(slug, "Nope"), refereeToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> send(HttpMethod method, String url, Object body, String token) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private String login(Role role) {
        String email = role.name().toLowerCase() + "-boardview-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName(role.name());
        user.setLastName("BoardView");
        user.setRoles(Set.of(role));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        ResponseEntity<AuthResponse> r = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().accessToken();
    }
}
