package dev.monkeypatch.rctiming.api.resultsexport;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.TestHttp;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The results export module (#799): switched off, its admin and board endpoints answer as if they did not exist. */
class ResultsExportModuleIT extends AbstractIntegrationTest {

    @Autowired
    TestHttp http;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ModuleSwitch moduleSwitch;

    private HttpEntity<Object> admin(Object body) {
        String email = "results-mod-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("pass12345"));
        user.setFirstName("Spon");
        user.setLastName("Sor");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);
        String token = http.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class)
                .getBody().accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void switchedOffTheExportEndpointsAnswer404ButTheRaceHubConnectionStays() {
        String exportsUrl = "/api/v1/admin/results-exports";
        String connectionUrl = "/api/v1/admin/racehub/connection";
        assertThat(http.exchange(exportsUrl, HttpMethod.GET, admin(null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        moduleSwitch.setEnabled(ResultsExportModule.ID, false, Actor.system("test"));
        try {
            assertThat(http.exchange(exportsUrl, HttpMethod.GET, admin(null), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(http.exchange("/api/v1/admin/results-site.zip", HttpMethod.GET, admin(null), String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(http.exchange(connectionUrl, HttpMethod.GET, admin(null), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        } finally {
            moduleSwitch.setEnabled(ResultsExportModule.ID, true, Actor.system("test"));
        }
        assertThat(http.exchange(exportsUrl, HttpMethod.GET, admin(null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
