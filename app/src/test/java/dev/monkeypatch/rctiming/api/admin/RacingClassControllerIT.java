package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.api.admin.dto.RacingClassDto;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RacingClassControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-class-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    void createClass_returns201() {
        String className = "RC-Open-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<RacingClassDto> response = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", className, "description", "Open electric class"),
                        adminHeaders()),
                RacingClassDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo(className);
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void listClasses_returnsAll() {
        String nameA = "RC-A-" + UUID.randomUUID().toString().substring(0, 8);
        String nameB = "RC-B-" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.exchange("/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", nameA), adminHeaders()), RacingClassDto.class);
        restTemplate.exchange("/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", nameB), adminHeaders()), RacingClassDto.class);

        ResponseEntity<List<RacingClassDto>> response = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<RacingClassDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<String> names = response.getBody().stream().map(RacingClassDto::name).toList();
        assertThat(names).contains(nameA, nameB);
    }

    @Test
    void updateClass_changesDescription() {
        ResponseEntity<RacingClassDto> created = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Nitro Stock", "description", "Old desc"), adminHeaders()),
                RacingClassDto.class);
        Long id = created.getBody().id();

        ResponseEntity<RacingClassDto> updated = restTemplate.exchange(
                "/api/v1/admin/classes/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Nitro Stock", "description", "Updated desc"),
                        adminHeaders()),
                RacingClassDto.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().description()).isEqualTo("Updated desc");
    }

    @Test
    void deleteClass_returns204() {
        ResponseEntity<RacingClassDto> created = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Delete Me Class"), adminHeaders()),
                RacingClassDto.class);
        Long id = created.getBody().id();

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/admin/classes/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void colour_isSavedLowerCasedAndCanBeCleared() {
        String name = "RC-Colour-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<RacingClassDto> created = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "colour", "#DC2626"), adminHeaders()),
                RacingClassDto.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().colour()).isEqualTo("#dc2626");

        ResponseEntity<RacingClassDto> cleared = restTemplate.exchange(
                "/api/v1/admin/classes/" + created.getBody().id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", name, "colour", ""), adminHeaders()),
                RacingClassDto.class);

        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().colour()).isNull();
    }

    @Test
    void colour_thatIsNotSixHexDigits_isRefused() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "RC-Bad-" + UUID.randomUUID(), "colour", "red"), adminHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- helpers ---

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void createAdminUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Admin");
        user.setLastName("Classes");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
