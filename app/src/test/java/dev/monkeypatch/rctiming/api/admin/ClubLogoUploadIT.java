package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.admin.dto.ClubProfileDto;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubLogoUploadIT extends AbstractIntegrationTest {

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("storage.local-path", () -> uploadDir.toString());
        registry.add("storage.public-base-url", () -> "http://localhost:8080/storage");
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenService jwtTokenService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // Ensure a club profile exists first
        String email = "admin-logo-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("adminPass123"));
        user.setFirstName("Admin");
        user.setLastName("Logo");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        ResponseEntity<AuthResponse> loginResp = rest.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        adminToken = loginResp.getBody().accessToken();

        // Create club profile so logo upload has a profile to attach to
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setBearerAuth(adminToken);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange("/api/v1/admin/club/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Test Club", "timezone", "Europe/London"), jsonHeaders),
                ClubProfileDto.class);
    }

    @Test
    void uploadLogo_asAdmin_storesObjectAndPersistsUrl() {
        // Minimal valid PNG header bytes
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'};

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(adminToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pngBytes) {
            @Override
            public String getFilename() { return "logo.png"; }
        });

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/admin/club/logo", HttpMethod.PUT, req, Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        String logoUrl = (String) resp.getBody().get("logoUrl");
        assertNotNull(logoUrl);
        assertTrue(logoUrl.contains("club-logos/"), "URL should contain club-logos/ prefix: " + logoUrl);

        // Assert the URL is persisted on the club profile
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(adminToken);
        ResponseEntity<ClubProfileDto> profile = rest.exchange(
                "/api/v1/admin/club/profile", HttpMethod.GET,
                new HttpEntity<>(getHeaders),
                ClubProfileDto.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertEquals(logoUrl, profile.getBody().logoUrl());

        // The change is in the audit log with the new address
        var rows = jdbc.queryForList(
                "select * from audit_log where action = 'CLUB_LOGO_CHANGED' and after_json like ?", "%" + logoUrl + "%");
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).get("actor_user_id"));
    }

    @Test
    void uploadLogo_rejectsNonImageContentType() {
        byte[] textBytes = "not an image".getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(adminToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // ByteArrayResource without explicit content type sends application/octet-stream
        body.add("file", new ByteArrayResource(textBytes) {
            @Override
            public String getFilename() { return "bad.txt"; }
        });

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/admin/club/logo", HttpMethod.PUT, req, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void uploadLogo_withNoOfficialRole_returns403() {
        // Accounts with no official role can no longer sign in; a token minted for one is still refused
        User noRoles = new User();
        noRoles.setEmail("noroles-logo-" + UUID.randomUUID() + "@test.com");
        noRoles.setPasswordHash(passwordEncoder.encode("password123"));
        noRoles.setFirstName("No");
        noRoles.setLastName("Roles");
        noRoles.setRoles(java.util.Set.of());
        noRoles.setCreatedAt(java.time.Instant.now());
        noRoles.setUpdatedAt(java.time.Instant.now());
        String racerToken = jwtTokenService.generateAccessToken(userRepository.save(noRoles));

        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G'};
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(racerToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pngBytes) {
            @Override
            public String getFilename() { return "logo.png"; }
        });

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/admin/club/logo", HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }
}
