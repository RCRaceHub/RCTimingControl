package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.api.admin.dto.RaceFormatTemplateDto;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FormatControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RaceFormatService raceFormatService;

    @Autowired
    RaceFormatTemplateRepository raceFormatTemplateRepository;

    @Autowired
    EventClassRepository eventClassRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-format-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    void createTimedFormat_returns201() {
        Map<String, Object> config = Map.of(
                "type", "TIMED",
                "durationMinutes", 5,
                "startType", "STAGGER",
                "qualifyingType", "FTQ",
                "racePaddingMinutes", 2,
                "staggerIntervalSeconds", 3
        );
        Map<String, Object> request = Map.of("name", "5-Minute Timed", "config", config);

        ResponseEntity<RaceFormatTemplateDto> response = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), RaceFormatTemplateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("5-Minute Timed");
    }

    @Test
    void createBumpUpFormat_returns201() {
        // Map.of() supports max 10 entries; use ofEntries for 11 fields
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("type", "BUMP_UP");
        config.put("qualifyingHeats", 3);
        config.put("heatDurationMinutes", 5);
        config.put("bestHeatsCount", 2);
        config.put("gridSize", 10);
        config.put("bumpSpots", 2);
        config.put("qualifyingStartType", "STAGGER");
        config.put("finalsStartType", "GRID");
        config.put("qualifyingType", "FTQ");
        config.put("racePaddingMinutes", 2);
        config.put("staggerIntervalSeconds", 3);
        Map<String, Object> request = Map.of("name", "Bump Up Format", "config", config);

        ResponseEntity<RaceFormatTemplateDto> response = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), RaceFormatTemplateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void createPointsFinalsFormat_returns201() {
        Map<String, Object> config = Map.of(
                "type", "POINTS_FINALS",
                "qualifyingHeats", 3,
                "finalsCount", 3,
                "finalDurationMinutes", 5,
                "heatDurationMinutes", 5,
                "qualifyingStartType", "STAGGER",
                "finalsStartType", "GRID",
                "qualifyingType", "FTQ",
                "racePaddingMinutes", 2,
                "staggerIntervalSeconds", 3
        );
        Map<String, Object> request = Map.of("name", "Points Finals Format", "config", config);

        ResponseEntity<RaceFormatTemplateDto> response = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), RaceFormatTemplateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void invalidTypeDiscriminator_returns400() {
        Map<String, Object> config = Map.of(
                "type", "INVALID",
                "durationMinutes", 5
        );
        Map<String, Object> request = Map.of("name", "Bad Format", "config", config);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exportJson_returnsValidJson() {
        // Create a format template first
        Map<String, Object> config = Map.of(
                "type", "TIMED",
                "durationMinutes", 10,
                "startType", "GRID",
                "qualifyingType", "FTQ",
                "racePaddingMinutes", 5,
                "staggerIntervalSeconds", 1
        );
        ResponseEntity<RaceFormatTemplateDto> created = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Export JSON Test", "config", config), adminHeaders()),
                RaceFormatTemplateDto.class);
        Long id = created.getBody().id();

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setBearerAuth(adminToken);
        jsonHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/formats/" + id + "/export", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody()).contains("TIMED");
    }

    @Test
    void exportYaml_returnsValidYaml() {
        Map<String, Object> config = Map.of(
                "type", "TIMED",
                "durationMinutes", 10,
                "startType", "GRID",
                "qualifyingType", "FTQ",
                "racePaddingMinutes", 5,
                "staggerIntervalSeconds", 1
        );
        ResponseEntity<RaceFormatTemplateDto> created = restTemplate.exchange(
                "/api/v1/admin/formats", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Export YAML Test", "config", config), adminHeaders()),
                RaceFormatTemplateDto.class);
        Long id = created.getBody().id();

        HttpHeaders yamlHeaders = new HttpHeaders();
        yamlHeaders.setBearerAuth(adminToken);
        yamlHeaders.set(HttpHeaders.ACCEPT, "application/yaml");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/formats/" + id + "/export", HttpMethod.GET,
                new HttpEntity<>(yamlHeaders), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        // YAML response should contain the type discriminator
        assertThat(response.getBody()).contains("TIMED");
    }

    @Test
    void importJson_createsTemplate() {
        String jsonBody = """
                {
                  "type": "TIMED",
                  "durationMinutes": 7,
                  "startType": "STAGGER",
                  "qualifyingType": "FTQ",
                  "racePaddingMinutes": 3,
                  "staggerIntervalSeconds": 2
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RaceFormatTemplateDto> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=JSON Import Test", HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), RaceFormatTemplateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void yamlImport_createsTemplate() {
        String yamlBody = """
                type: TIMED
                durationMinutes: 10
                startType: GRID
                qualifyingType: FTQ
                racePaddingMinutes: 5
                staggerIntervalSeconds: 1
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.parseMediaType("application/yaml"));

        ResponseEntity<RaceFormatTemplateDto> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=YAML Import Test", HttpMethod.POST,
                new HttpEntity<>(yamlBody, headers), RaceFormatTemplateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void importInvalidSchema_returns400() {
        String badJson = "{ \"notAValidFormat\": true }";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=Bad Import", HttpMethod.POST,
                new HttpEntity<>(badJson, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exportOfAMissingFormat_returns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.set(HttpHeaders.ACCEPT, "application/yaml");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/formats/999999/export", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void importOfAYamlNull_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.parseMediaType("application/yaml"));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=Null", HttpMethod.POST,
                new HttpEntity<>("~\n", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importOfAnUnknownType_namesTheTypes_notTheJavaClass() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.parseMediaType("application/yaml"));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=Odd", HttpMethod.POST,
                new HttpEntity<>("type: RELAY\n", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").toString())
                .contains("TIMED, BUMP_UP, POINTS_FINALS")
                .doesNotContain("dev.monkeypatch");
    }

    @Test
    void importOfAnEmptyYamlFile_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.parseMediaType("application/yaml"));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/formats/import?name=Empty", HttpMethod.POST,
                new HttpEntity<>("# nothing here\n", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void snapshotImmutability_templateEditDoesNotAffectEventClass() {
        // Create template with 5-minute timed config
        TimedRaceConfig originalConfig = new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3);
        RaceFormatTemplate template = raceFormatService.create(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), "Snapshot Test Template", originalConfig);

        // Assign template to EventClass — creates a snapshot
        EventClass eventClass = raceFormatService.assignTemplateToEventClass(template);
        eventClass.setCreatedAt(Instant.now());
        eventClass.setUpdatedAt(Instant.now());
        EventClass saved = eventClassRepository.save(eventClass);

        // Now update the template via the API (changing duration to 10 minutes)
        Map<String, Object> updatedConfig = Map.of(
                "type", "TIMED",
                "durationMinutes", 10,
                "startType", "STAGGER",
                "qualifyingType", "FTQ",
                "racePaddingMinutes", 2,
                "staggerIntervalSeconds", 3
        );
        restTemplate.exchange(
                "/api/v1/admin/formats/" + template.getId(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Updated Template", "config", updatedConfig), adminHeaders()),
                RaceFormatTemplateDto.class);

        // Reload the event class and verify snapshot is unchanged
        EventClass reloaded = eventClassRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getConfigSnapshot()).isInstanceOf(TimedRaceConfig.class);
        TimedRaceConfig snapshot = (TimedRaceConfig) reloaded.getConfigSnapshot();
        assertThat(snapshot.durationMinutes())
                .as("Snapshot should still have original 5 minutes, not the updated 10")
                .isEqualTo(5);
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
        user.setLastName("Format");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
