package dev.monkeypatch.rctiming.api.setup;

import com.fasterxml.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The setup wizard's network-name endpoint (#604): who may read and change it, and what it refuses. A valid change
 * is not saved here, because it writes {@code application.properties} in the data folder.
 */
class NetworkNameControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/setup/network-name";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void anAdminSeesTheSuggestionAndWhatIsRunning() {
        var resp = restTemplate.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers(Role.ADMIN)), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("suggestedName").asText()).isEqualTo("rctiming");
        assertThat(resp.getBody().get("restartNeeded").asBoolean()).isFalse();
    }

    @Test
    void aNameThatIsNotAHostName_isRefused() {
        var resp = restTemplate.exchange(URL, HttpMethod.PUT,
                new HttpEntity<>("{\"name\": \"my laptop!\"}", headers(Role.ADMIN)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRaceDirectorIsForbidden() {
        var get = restTemplate.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers(Role.RACE_DIRECTOR)), String.class);
        var put = restTemplate.exchange(URL, HttpMethod.PUT,
                new HttpEntity<>("{\"name\": \"rctiming\"}", headers(Role.RACE_DIRECTOR)), String.class);

        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousIsUnauthorised() {
        var resp = restTemplate.getForEntity(URL, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders headers(Role role) {
        String email = "netname-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(Set.of(role));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(login.getBody().accessToken());
        return headers;
    }
}
