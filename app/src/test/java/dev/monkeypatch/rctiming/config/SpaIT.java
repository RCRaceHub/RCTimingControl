package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.api.pub.AboutController.AboutDto;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend served from the app (#23). The test classpath carries a stand-in
 * {@code static/index.html} and one asset, as the packaged jar carries the real build.
 */
class SpaIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @LocalServerPort int port;

    @Test
    void theRootAndDeepLinksServeTheAppWithoutSigningIn() {
        for (String path : new String[] {"/", "/race-control/12", "/admin/backups", "/results/3"}) {
            ResponseEntity<String> page = restTemplate.getForEntity(path, String.class);
            assertThat(page.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(page.getHeaders().getContentType()).as(path).isNotNull();
            assertThat(page.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).as(path).isTrue();
            assertThat(page.getBody()).as(path).contains("<div id=\"root\">");
        }
    }

    @Test
    void pagesMayBeFramedBySameOriginPagesOnly() {
        // The Overlay Designer previews the overlay in an iframe (#524)
        ResponseEntity<String> page = restTemplate.getForEntity("/boards/overlay", String.class);
        assertThat(page.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }

    @Test
    void assetsAreCachedForGoodAndMissingOnesAre404() {
        ResponseEntity<String> asset = restTemplate.getForEntity("/assets/app-test.js", String.class);
        assertThat(asset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asset.getHeaders().getCacheControl()).contains("immutable").contains("max-age=31536000");

        assertThat(restTemplate.getForEntity("/assets/missing.js", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/", String.class).getHeaders().getCacheControl())
                .isEqualTo("no-cache");
    }

    @Test
    void theApiStaysProtectedAndUnknownApiPathsAreNotPages() {
        assertThat(restTemplate.getForEntity("/api/v1/competitors", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // An encoded path routes to the same controller, so it must not pass as a page
        URI encoded = URI.create("http://localhost:" + port + "/%61pi/v1/competitors");
        assertThat(restTemplate.getForEntity(encoded, String.class).getStatusCode())
                .isNotEqualTo(HttpStatus.OK);

        ResponseEntity<String> unknown = restTemplate.exchange("/api/v1/no-such-thing", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).doesNotContain("<div id=\"root\">");

        for (String root : new String[] {"/api", "/api/", "/ws", "/storage", "/actuator", "/error"}) {
            ResponseEntity<String> page = restTemplate.getForEntity(root, String.class);
            assertThat(page.getStatusCode()).as(root).isNotEqualTo(HttpStatus.OK);
            assertThat(String.valueOf(page.getBody())).as(root).doesNotContain("<div id=\"root\">");
        }
    }

    @Test
    void aboutListsAddressesOnThisPort() {
        AboutDto about = restTemplate.getForObject("/api/v1/about", AboutDto.class);
        assertThat(about.addresses()).allSatisfy(url ->
                assertThat(url).startsWith("http://").endsWith(":" + port + "/"));
    }

    private HttpHeaders adminHeaders() {
        String email = "admin-spa-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("adminPass123"));
        user.setFirstName("Admin");
        user.setLastName("Spa");
        user.setRoles(Set.of(Role.ADMIN));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        AuthResponse auth = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"), AuthResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.accessToken());
        return headers;
    }
}
