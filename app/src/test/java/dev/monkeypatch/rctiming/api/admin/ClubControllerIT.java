package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ClubProfileDto;
import dev.monkeypatch.rctiming.api.admin.dto.GoverningBodyAffiliationDto;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClubControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-club-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    void createProfile_validRequest_returns200() {
        Map<String, Object> request = Map.of(
                "name", "Monkeypatch RC Club",
                "email", "club@example.com",
                "phone", "01234567890",
                "latitude", 51.5074,
                "longitude", -0.1278,
                "timezone", "Europe/London"
        );

        ResponseEntity<ClubProfileDto> response = restTemplate.exchange(
                "/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()), ClubProfileDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Monkeypatch RC Club");
        assertThat(response.getBody().timezone()).isEqualTo("Europe/London");
    }

    @Test
    void createProfile_invalidTimezone_returns400() {
        Map<String, Object> request = Map.of(
                "name", "RC Club",
                "timezone", "Invalid/Zone"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getProfile_exists_returns200() {
        // Create profile first
        Map<String, Object> request = Map.of(
                "name", "Get Profile Club",
                "timezone", "Europe/London"
        );
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()), ClubProfileDto.class);

        ResponseEntity<ClubProfileDto> response = restTemplate.exchange(
                "/api/v1/admin/club/profile", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), ClubProfileDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Get Profile Club");
    }

    @Test
    void createAffiliation_validRequest_returns201() {
        // Ensure profile exists first
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Club", "timezone", "Europe/London"), adminHeaders()),
                ClubProfileDto.class);

        Map<String, Object> request = Map.of(
                "code", "BRCA-" + UUID.randomUUID().toString().substring(0, 6),
                "displayName", "British Radio Car Association",
                "membershipRequired", true
        );

        ResponseEntity<GoverningBodyAffiliationDto> response = restTemplate.exchange(
                "/api/v1/admin/club/affiliations", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), GoverningBodyAffiliationDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().membershipRequired()).isTrue();
    }

    @Test
    void createAffiliation_duplicateCode_returns409() {
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Club", "timezone", "Europe/London"), adminHeaders()),
                ClubProfileDto.class);

        String code = "DUP-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> request = Map.of(
                "code", code,
                "displayName", "Test Org",
                "membershipRequired", false
        );

        restTemplate.exchange("/api/v1/admin/club/affiliations", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), GoverningBodyAffiliationDto.class);

        ResponseEntity<Map> second = restTemplate.exchange(
                "/api/v1/admin/club/affiliations", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateAffiliation_changesMembershipRequired() {
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Club", "timezone", "Europe/London"), adminHeaders()),
                ClubProfileDto.class);

        String code = "UPD-" + UUID.randomUUID().toString().substring(0, 6);
        ResponseEntity<GoverningBodyAffiliationDto> created = restTemplate.exchange(
                "/api/v1/admin/club/affiliations", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code, "displayName", "Test Org", "membershipRequired", false),
                        adminHeaders()),
                GoverningBodyAffiliationDto.class);
        Long id = created.getBody().id();

        Map<String, Object> updateRequest = Map.of(
                "code", code,
                "displayName", "Test Org Updated",
                "membershipRequired", true
        );
        ResponseEntity<GoverningBodyAffiliationDto> updated = restTemplate.exchange(
                "/api/v1/admin/club/affiliations/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateRequest, adminHeaders()), GoverningBodyAffiliationDto.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().membershipRequired()).isTrue();
    }

    @Test
    void deleteAffiliation_returns204() {
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Club", "timezone", "Europe/London"), adminHeaders()),
                ClubProfileDto.class);

        String code = "DEL-" + UUID.randomUUID().toString().substring(0, 6);
        ResponseEntity<GoverningBodyAffiliationDto> created = restTemplate.exchange(
                "/api/v1/admin/club/affiliations", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code, "displayName", "Test Org", "membershipRequired", false),
                        adminHeaders()),
                GoverningBodyAffiliationDto.class);
        Long id = created.getBody().id();

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/admin/club/affiliations/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // --- helpers ---

    @Test
    void privacyNotice_defaultNamesTheClubAndHowToReachIt_andIsOpenToAnyone() {
        restTemplate.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Privacy Test Club", "email", "privacy@example.com",
                        "timezone", "Europe/London"), adminHeaders()), ClubProfileDto.class);
        restTemplate.exchange("/api/v1/admin/club/privacy-notice", HttpMethod.PUT,
                new HttpEntity<>(Map.of("text", ""), adminHeaders()), Map.class);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/privacy-notice", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("clubName")).isEqualTo("Privacy Test Club");
        assertThat((String) response.getBody().get("text"))
                .contains("Privacy Test Club").contains("privacy@example.com");
    }

    @Test
    void privacyNotice_adminCanWriteOwnTextAndGoBackToTheDefault() {
        ResponseEntity<Map> saved = restTemplate.exchange("/api/v1/admin/club/privacy-notice", HttpMethod.PUT,
                new HttpEntity<>(Map.of("text", "Our own words."), adminHeaders()), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("customised")).isEqualTo(true);

        ResponseEntity<Map> publicView = restTemplate.getForEntity("/api/v1/privacy-notice", Map.class);
        assertThat(publicView.getBody().get("text")).isEqualTo("Our own words.");

        ResponseEntity<Map> reset = restTemplate.exchange("/api/v1/admin/club/privacy-notice", HttpMethod.PUT,
                new HttpEntity<>(Map.of("text", "  "), adminHeaders()), Map.class);
        assertThat(reset.getBody().get("customised")).isEqualTo(false);
        assertThat((String) reset.getBody().get("text")).doesNotContain("Our own words.");
    }

    @Test
    void privacyNotice_onlyAnAdminCanChangeIt() {
        String email = "director-club-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("directorPass123"));
        user.setFirstName("Race");
        user.setLastName("Director");
        user.setRoles(Set.of(Role.RACE_DIRECTOR));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        String token = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "directorPass123"), AuthResponse.class).getBody().accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/admin/club/privacy-notice", HttpMethod.PUT,
                new HttpEntity<>(Map.of("text", "Not allowed"), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.getForEntity("/api/v1/privacy-notice", Map.class).getBody().get("text"))
                .isNotEqualTo("Not allowed");
    }

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
        user.setLastName("User");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
