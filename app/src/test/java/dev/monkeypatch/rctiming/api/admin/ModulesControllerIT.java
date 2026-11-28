package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.TestHttp;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import dev.monkeypatch.rctiming.modules.RequiresModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The Modules API (#797), with one test module and a controller behind it. */
@Import({ModulesControllerIT.TestModule.class, ModulesControllerIT.PingController.class})
class ModulesControllerIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class TestModule {
        @Bean
        ModuleDescriptor itModule() {
            return new ModuleDescriptor("it-module", "Test module", "Only for tests", "1");
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin/it-module")
    @RequiresModule("it-module")
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }

    @Autowired
    TestHttp http;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JdbcTemplate jdbc;

    private String token(Role role) {
        String email = "modules-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("pass12345"));
        user.setFirstName("Mod");
        user.setLastName("Test");
        user.setRoles(Set.of(role));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);
        return http.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class)
                .getBody().accessToken();
    }

    private HttpEntity<Object> as(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private List<Map<String, Object>> list(String token, String url) {
        return http.exchange(url, HttpMethod.GET, as(token, null),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }

    private Map<String, Object> itModule(List<Map<String, Object>> modules) {
        return modules.stream().filter(m -> "it-module".equals(m.get("id"))).findFirst().orElseThrow();
    }

    @Test
    void anAdminSwitchesAModuleOffAndTheGuardedEndpointAnswers404ThenOnAgain() {
        String admin = token(Role.ADMIN);
        assertThat(itModule(list(admin, "/api/v1/admin/modules")).get("enabled")).isEqualTo(true);
        assertThat(http.exchange("/api/v1/admin/it-module/ping", HttpMethod.GET, as(admin, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(http.exchange("/api/v1/admin/modules/it-module", HttpMethod.PUT,
                as(admin, Map.of("enabled", false)), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(itModule(list(admin, "/api/v1/admin/modules")).get("enabled")).isEqualTo(false);
        assertThat(http.exchange("/api/v1/admin/it-module/ping", HttpMethod.GET, as(admin, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'MODULE_SWITCHED' and entity_id = 'it-module'",
                Integer.class)).isPositive();

        http.exchange("/api/v1/admin/modules/it-module", HttpMethod.PUT, as(admin, Map.of("enabled", true)), Void.class);
        assertThat(http.exchange("/api/v1/admin/it-module/ping", HttpMethod.GET, as(admin, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aRaceDirectorCanReadButNotSwitchTheClubSwitch() {
        String director = token(Role.RACE_DIRECTOR);

        assertThat(itModule(list(director, "/api/v1/admin/modules"))).isNotNull();
        assertThat(http.exchange("/api/v1/admin/modules/it-module", HttpMethod.PUT,
                as(director, Map.of("enabled", false)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anEventCanTurnAModuleOffAndAnUnknownModuleIs404() {
        String director = token(Role.RACE_DIRECTOR);
        long eventId = jdbc.queryForObject("select id from events order by id limit 1", Long.class);
        String url = "/api/v1/admin/events/" + eventId + "/modules";

        assertThat(http.exchange(url + "/it-module", HttpMethod.PUT, as(director, Map.of("off", true)), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> forEvent = itModule(list(director, url));
        assertThat(forEvent.get("enabled")).isEqualTo(false);
        assertThat(forEvent.get("clubEnabled")).isEqualTo(true);
        assertThat(forEvent.get("eventOff")).isEqualTo(true);

        http.exchange(url + "/it-module", HttpMethod.PUT, as(director, Map.of("off", false)), Void.class);
        assertThat(itModule(list(director, url)).get("enabled")).isEqualTo(true);

        assertThat(http.exchange(url + "/nope", HttpMethod.PUT, as(director, Map.of("off", true)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/v1/admin/events/999999999/modules", HttpMethod.GET, as(director, null),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
