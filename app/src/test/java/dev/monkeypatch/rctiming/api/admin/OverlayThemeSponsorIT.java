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

/** The sponsors an overlay theme owns: manage them, the public bar reads them, the fallback, who may change them and the cascade on delete. */
@SuppressWarnings({"rawtypes", "unchecked"})
class OverlayThemeSponsorIT extends AbstractIntegrationTest {

    private static final String THEMES_URL = "/api/v1/admin/overlay-themes";
    private static final String PUBLIC_SPONSORS_URL = "/api/v1/boards/sponsors";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String directorToken;
    private String refereeToken;
    private long themeId;

    @BeforeEach
    void setUp() {
        directorToken = login(Role.RACE_DIRECTOR);
        refereeToken = login(Role.REFEREE);
        ResponseEntity<Map> created = send(HttpMethod.POST, THEMES_URL,
                Map.of("name", "it-" + UUID.randomUUID().toString().substring(0, 8), "design", Map.of("theme", "dark")),
                directorToken);
        themeId = ((Number) created.getBody().get("id")).longValue();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from overlay_themes where name like 'it-%'");
    }

    private String sponsorsUrl() {
        return THEMES_URL + "/" + themeId + "/sponsors";
    }

    private long addSponsor(String name) {
        ResponseEntity<Map> r = send(HttpMethod.POST, sponsorsUrl(), Map.of("name", name), directorToken);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    @Test
    void aDirectorKeepsAThemesSponsorsInOrderAndTheOverlayReadsThemWithoutSigningIn() {
        long first = addSponsor("Acme Tyres");
        long second = addSponsor("Bolt Motors");

        ResponseEntity<Map> renamed = send(HttpMethod.PUT, sponsorsUrl() + "/" + first, Map.of("name", "Acme Rubber"), directorToken);
        assertThat(renamed.getBody().get("name")).isEqualTo("Acme Rubber");

        ResponseEntity<List> reordered = restTemplate.exchange(sponsorsUrl() + "/order", HttpMethod.PUT,
                new HttpEntity<>(Map.of("ids", List.of(second, first)), headers(directorToken)), List.class);
        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> shown = restTemplate.getForEntity(PUBLIC_SPONSORS_URL + "?themeId=" + themeId, List.class);
        assertThat(((List<Map>) shown.getBody()).stream().map(s -> s.get("name")))
                .containsExactly("Bolt Motors", "Acme Rubber");

        assertThat(send(HttpMethod.DELETE, sponsorsUrl() + "/" + first, null, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((List<Map>) restTemplate.getForEntity(PUBLIC_SPONSORS_URL + "?themeId=" + themeId, List.class).getBody())
                .stream().map(s -> s.get("name"))).containsExactly("Bolt Motors");
    }

    @Test
    void aThemeWithNoSponsorsOrAnUnknownThemeFallsBackToTheEventsSponsors() {
        List<Map> withoutTheme = restTemplate.getForEntity(PUBLIC_SPONSORS_URL, List.class).getBody();

        assertThat(restTemplate.getForEntity(PUBLIC_SPONSORS_URL + "?themeId=" + themeId, List.class).getBody())
                .isEqualTo(withoutTheme);
        assertThat(restTemplate.getForEntity(PUBLIC_SPONSORS_URL + "?themeId=999999999", List.class).getBody())
                .isEqualTo(withoutTheme);
    }

    @Test
    void aSponsorOfAnotherThemeIsNotFoundAndABlankNameIsRefused() {
        long sponsor = addSponsor("Acme Tyres");
        ResponseEntity<Map> other = send(HttpMethod.POST, THEMES_URL,
                Map.of("name", "it-other-" + UUID.randomUUID().toString().substring(0, 6), "design", Map.of()), directorToken);
        long otherId = ((Number) other.getBody().get("id")).longValue();

        assertThat(send(HttpMethod.PUT, THEMES_URL + "/" + otherId + "/sponsors/" + sponsor, Map.of("name", "X"), directorToken)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, sponsorsUrl(), Map.of("name", " "), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRefereeCanReadButNotChangeAThemesSponsors() {
        addSponsor("Acme Tyres");

        assertThat(restTemplate.exchange(sponsorsUrl(), HttpMethod.GET, new HttpEntity<>(headers(refereeToken)), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.POST, sponsorsUrl(), Map.of("name", "Nope"), refereeToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletingAThemeDeletesItsSponsors() {
        addSponsor("Acme Tyres");

        send(HttpMethod.DELETE, THEMES_URL + "/" + themeId, null, directorToken);

        assertThat(jdbc.queryForObject("select count(*) from overlay_theme_sponsors where theme_id = ?", Integer.class, themeId))
                .isZero();
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
        String email = role.name().toLowerCase() + "-themesponsor-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName(role.name());
        user.setLastName("OverlayThemeSponsor");
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
