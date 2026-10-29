package dev.monkeypatch.rctiming.api.setup;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.TestTables;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.setup.dto.BootstrapRequest;
import dev.monkeypatch.rctiming.api.setup.dto.DecoderConfigDto;
import dev.monkeypatch.rctiming.api.setup.dto.SetupProgressDto;
import dev.monkeypatch.rctiming.api.setup.dto.SetupStatusDto;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.track.Track;
import dev.monkeypatch.rctiming.domain.track.TrackRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SetupControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ClubProfileRepository clubProfileRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanUp() {
        trackRepository.deleteAll();
        clubProfileRepository.deleteAll();
        TestTables.truncateCascade(jdbcTemplate, transactionTemplate, "users", "race_format_templates");
    }

    @Test
    void getStatus_returnsSetupComplete_false_whenNoClub() {
        ResponseEntity<SetupStatusDto> response = restTemplate.getForEntity("/api/v1/setup/status", SetupStatusDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().bootstrapped()).isFalse();
        assertThat(response.getBody().setupComplete()).isFalse();
    }

    @Test
    void getStatus_returnsSetupComplete_true_afterClubSaved() {
        restTemplate.postForEntity("/api/v1/setup/bootstrap",
                new BootstrapRequest("Admin", "User", "admin@test.com", "password123"),
                AuthResponse.class);
        clubProfileRepository.save(minimalClub());
        ResponseEntity<SetupStatusDto> response = restTemplate.getForEntity("/api/v1/setup/status", SetupStatusDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bootstrapped()).isTrue();
        assertThat(response.getBody().setupComplete()).isTrue();
    }

    @Test
    void bootstrap_createsAdminUserAndReturnsToken() {
        BootstrapRequest req = new BootstrapRequest("Admin", "User", "admin@test.com", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/v1/setup/bootstrap", req, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().roles()).contains("ADMIN");
        assertThat(response.getBody().roles()).doesNotContain("RACER");
    }

    @Test
    void bootstrap_recordsTheFirstAdminInTheOfficialsLogWithNoActor() {
        restTemplate.postForEntity("/api/v1/setup/bootstrap",
                new BootstrapRequest("Admin", "User", "admin@test.com", "password123"), AuthResponse.class);

        var rows = jdbcTemplate.queryForList(
                "select a.action, a.actor_user_id from official_audit_log a"
                        + " join users u on u.id = a.official_user_id where u.email = ?", "admin@test.com");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "ADDED");
        assertThat(rows.get(0).get("actor_user_id")).isNull();
    }

    @Test
    void createStaff_isRecordedWithWhoAddedThemAndRefusesADuplicateEmail() {
        ResponseEntity<AuthResponse> bootstrap = restTemplate.postForEntity("/api/v1/setup/bootstrap",
                new BootstrapRequest("Admin", "User", "admin@test.com", "password123"), AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bootstrap.getBody().accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Long adminId = Long.valueOf(bootstrap.getBody().id());
        java.util.Map<String, Object> staff = java.util.Map.of(
                "firstName", "Rae", "lastName", "Feree", "email", "rae@test.com",
                "password", "password123", "roles", java.util.List.of("REFEREE"));

        ResponseEntity<Void> created = restTemplate.exchange("/api/v1/setup/staff", HttpMethod.POST,
                new HttpEntity<>(staff, headers), Void.class);
        ResponseEntity<Void> duplicate = restTemplate.exchange("/api/v1/setup/staff", HttpMethod.POST,
                new HttpEntity<>(staff, headers), Void.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var rows = jdbcTemplate.queryForList(
                "select a.action, a.actor_user_id, a.detail from official_audit_log a"
                        + " join users u on u.id = a.official_user_id where u.email = ?", "rae@test.com");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "ADDED");
        assertThat(((Number) rows.get(0).get("actor_user_id")).longValue()).isEqualTo(adminId);
        assertThat((String) rows.get(0).get("detail")).contains("REFEREE");
    }

    @Test
    void bootstrap_returns409_whenUsersExist() {
        BootstrapRequest req = new BootstrapRequest("Admin", "User", "admin@test.com", "password123");
        restTemplate.postForEntity("/api/v1/setup/bootstrap", req, AuthResponse.class);
        ResponseEntity<Void> second = restTemplate.postForEntity("/api/v1/setup/bootstrap", req, Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getProgress_reflectsDataState() {
        // Bootstrap creates an admin user (counts toward staff check)
        BootstrapRequest req = new BootstrapRequest("Admin", "User", "admin@test.com", "password123");
        ResponseEntity<AuthResponse> bootstrapResp = restTemplate.postForEntity("/api/v1/setup/bootstrap", req, AuthResponse.class);
        assertThat(bootstrapResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String jwt = bootstrapResp.getBody().accessToken();

        // After bootstrap: staff=false — bootstrap admin alone does not satisfy Step 4;
        // requires a second official created through the wizard
        SetupProgressDto progress1 = getProgress(jwt);
        assertThat(progress1.club()).isFalse();
        assertThat(progress1.track()).isFalse();
        assertThat(progress1.format()).isFalse();
        assertThat(progress1.staff()).isFalse();
        assertThat(progress1.decoder()).isFalse();

        // Add club profile: club=true, decoder still false (no host/port/protocol)
        clubProfileRepository.save(minimalClub());
        SetupProgressDto progress2 = getProgress(jwt);
        assertThat(progress2.club()).isTrue();
        assertThat(progress2.decoder()).isFalse();

        // Add track: track=true
        Track track = new Track();
        track.setName("Test Track");
        track.setCreatedAt(Instant.now());
        track.setUpdatedAt(Instant.now());
        trackRepository.save(track);
        SetupProgressDto progress3 = getProgress(jwt);
        assertThat(progress3.track()).isTrue();
    }

    @Test
    void getDecoderConfig_returnsStoredSettings() {
        BootstrapRequest req = new BootstrapRequest("Admin", "User", "admin@test.com", "password123");
        ResponseEntity<AuthResponse> bootstrapResp = restTemplate.postForEntity("/api/v1/setup/bootstrap", req, AuthResponse.class);
        assertThat(bootstrapResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String jwt = bootstrapResp.getBody().accessToken();

        // Saved straight to the repository so no decoder listener connection is attempted.
        ClubProfile club = minimalClub();
        club.setDecoderHost("192.168.1.50");
        club.setDecoderPort(5100);
        club.setDecoderProtocol("RC4");
        clubProfileRepository.save(club);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        ResponseEntity<DecoderConfigDto> resp = restTemplate.exchange(
                "/api/v1/setup/decoder-config",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                DecoderConfigDto.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(new DecoderConfigDto("192.168.1.50", 5100, "RC4"));
    }

    // --- helpers ---

    private SetupProgressDto getProgress(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<SetupProgressDto> resp = restTemplate.exchange(
                "/api/v1/setup/progress",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                SetupProgressDto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private ClubProfile minimalClub() {
        ClubProfile profile = new ClubProfile();
        profile.setName("Test Club");
        profile.setTimezone("Europe/London");
        Instant now = Instant.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profile;
    }
}
