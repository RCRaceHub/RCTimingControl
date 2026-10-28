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

/** The saved overlay themes: the seeded BRCA and Club themes, the anonymous read, create, change, delete, validation and who may change them. */
@SuppressWarnings({"rawtypes", "unchecked"})
class OverlayThemeControllerIT extends AbstractIntegrationTest {

    private static final String ADMIN_URL = "/api/v1/admin/overlay-themes";
    private static final String PUBLIC_URL = "/api/v1/boards/overlay-themes/";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String directorToken;
    private String refereeToken;
    private String name;

    @BeforeEach
    void setUp() {
        name = "it-" + UUID.randomUUID().toString().substring(0, 8);
        directorToken = login(Role.RACE_DIRECTOR);
        refereeToken = login(Role.REFEREE);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from overlay_themes where name like 'it-%'");
    }

    private Map<String, Object> body(String themeName) {
        return Map.of("name", themeName, "design", Map.of("theme", "light", "top", 6, "tickerItems", List.of("next")));
    }

    @Test
    void theStarterThemesAreSeededAndListed() {
        ResponseEntity<List> list = restTemplate.exchange(ADMIN_URL, HttpMethod.GET,
                new HttpEntity<>(headers(refereeToken)), List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<Map>) list.getBody()).stream().map(t -> t.get("name"))).contains("BRCA", "Club");
    }

    @Test
    void aRaceDirectorCreatesChangesAndDeletesAThemeAndAnyoneCanReadIt() {
        ResponseEntity<Map> created = send(HttpMethod.POST, ADMIN_URL, body(name), directorToken);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = ((Number) created.getBody().get("id")).longValue();
        assertThat(((Map) created.getBody().get("design")).get("top")).isEqualTo(6);

        assertThat(restTemplate.getForEntity(PUBLIC_URL + id, Map.class).getBody().get("name")).isEqualTo(name);

        ResponseEntity<Map> changed = send(HttpMethod.PUT, ADMIN_URL + "/" + id, body(name + "-b"), directorToken);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("name")).isEqualTo(name + "-b");

        assertThat(send(HttpMethod.DELETE, ADMIN_URL + "/" + id, null, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(PUBLIC_URL + id, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aTakenNameIsAConflictAndABadDesignIsRefused() {
        assertThat(send(HttpMethod.POST, ADMIN_URL, body(name), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(send(HttpMethod.POST, ADMIN_URL, body(name), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.POST, ADMIN_URL, Map.of("name", name + "-x", "design", List.of(1)), directorToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, ADMIN_URL, Map.of("name", " ", "design", Map.of()), directorToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRefereeCannotChangeATheme() {
        assertThat(send(HttpMethod.POST, ADMIN_URL, body(name), refereeToken).getStatusCode())
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
        String email = role.name().toLowerCase() + "-overlaytheme-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName(role.name());
        user.setLastName("OverlayTheme");
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
