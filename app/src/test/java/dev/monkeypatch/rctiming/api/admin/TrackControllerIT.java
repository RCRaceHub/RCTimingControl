package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.api.admin.dto.DecoderLoopDto;
import dev.monkeypatch.rctiming.api.admin.dto.TrackDto;
import dev.monkeypatch.rctiming.api.admin.dto.TrackLapThresholdDto;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
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

class TrackControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-track-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    void createTrack_returns201() {
        ResponseEntity<TrackDto> response = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Main Track"), adminHeaders()),
                TrackDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Main Track");
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void listTracks_returnsAll() {
        restTemplate.exchange("/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Track Alpha"), adminHeaders()), TrackDto.class);
        restTemplate.exchange("/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Track Beta"), adminHeaders()), TrackDto.class);

        ResponseEntity<List<TrackDto>> response = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<TrackDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<String> names = response.getBody().stream().map(TrackDto::name).toList();
        assertThat(names).contains("Track Alpha", "Track Beta");
    }

    @Test
    void updateTrack_changesName() {
        ResponseEntity<TrackDto> created = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Old Name"), adminHeaders()), TrackDto.class);
        Long id = created.getBody().id();

        ResponseEntity<TrackDto> updated = restTemplate.exchange(
                "/api/v1/admin/tracks/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "New Name"), adminHeaders()), TrackDto.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("New Name");
    }

    @Test
    void deleteTrack_returns204() {
        ResponseEntity<TrackDto> created = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Delete Me"), adminHeaders()), TrackDto.class);
        Long id = created.getBody().id();

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/admin/tracks/" + id, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void addDecoderLoop_returns201() {
        ResponseEntity<TrackDto> track = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Loop Track"), adminHeaders()), TrackDto.class);
        Long trackId = track.getBody().id();

        Map<String, Object> loopRequest = Map.of(
                "loopId", "LOOP_01",
                "displayName", "Chicane Loop",
                "loopType", "CHICANE",
                "isScoringLoop", false
        );
        ResponseEntity<DecoderLoopDto> response = restTemplate.exchange(
                "/api/v1/admin/tracks/" + trackId + "/loops", HttpMethod.POST,
                new HttpEntity<>(loopRequest, adminHeaders()), DecoderLoopDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().loopId()).isEqualTo("LOOP_01");
        assertThat(response.getBody().loopType()).isEqualTo("CHICANE");
    }

    @Test
    void addDecoderLoop_scoringFlagPersisted() {
        ResponseEntity<TrackDto> track = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Scoring Track"), adminHeaders()), TrackDto.class);
        Long trackId = track.getBody().id();

        Map<String, Object> loopRequest = Map.of(
                "loopId", "SCORING_01",
                "displayName", "Finish Line",
                "loopType", "FINISH_LINE",
                "isScoringLoop", true
        );
        restTemplate.exchange("/api/v1/admin/tracks/" + trackId + "/loops", HttpMethod.POST,
                new HttpEntity<>(loopRequest, adminHeaders()), DecoderLoopDto.class);

        ResponseEntity<TrackDto> fetched = restTemplate.exchange(
                "/api/v1/admin/tracks/" + trackId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), TrackDto.class);

        assertThat(fetched.getBody().decoderLoops()).hasSize(1);
        assertThat(fetched.getBody().decoderLoops().get(0).isScoringLoop()).isTrue();
    }

    @Test
    void setLapThreshold_trackWideDefault() {
        ResponseEntity<TrackDto> track = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Threshold Track"), adminHeaders()), TrackDto.class);
        Long trackId = track.getBody().id();

        Map<String, Object> thresholdRequest = Map.of(
                "minLapMs", 5000
        );
        ResponseEntity<TrackLapThresholdDto> response = restTemplate.exchange(
                "/api/v1/admin/tracks/" + trackId + "/thresholds", HttpMethod.POST,
                new HttpEntity<>(thresholdRequest, adminHeaders()), TrackLapThresholdDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().minLapMs()).isEqualTo(5000);
        assertThat(response.getBody().racingClassId()).isNull();
    }

    @Test
    void setLapThreshold_classSpecific() {
        ResponseEntity<TrackDto> track = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Class Track"), adminHeaders()), TrackDto.class);
        Long trackId = track.getBody().id();

        // Create racing class first — use UUID suffix to avoid unique-name conflicts with other test classes
        String className = "Track-Class-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<dev.monkeypatch.rctiming.api.admin.dto.RacingClassDto> racingClass = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", className), adminHeaders()),
                dev.monkeypatch.rctiming.api.admin.dto.RacingClassDto.class);
        assertThat(racingClass.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long classId = racingClass.getBody().id();

        Map<String, Object> thresholdRequest = Map.of(
                "racingClassId", classId,
                "minLapMs", 4000
        );
        ResponseEntity<TrackLapThresholdDto> response = restTemplate.exchange(
                "/api/v1/admin/tracks/" + trackId + "/thresholds", HttpMethod.POST,
                new HttpEntity<>(thresholdRequest, adminHeaders()), TrackLapThresholdDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().racingClassId()).isEqualTo(classId);
    }

    @Test
    void setLapThreshold_maxLastLapMs_persisted() {
        ResponseEntity<TrackDto> track = restTemplate.exchange(
                "/api/v1/admin/tracks", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "MaxLap Track"), adminHeaders()), TrackDto.class);
        Long trackId = track.getBody().id();

        Map<String, Object> thresholdRequest = Map.of(
                "minLapMs", 5000,
                "maxLastLapMs", 30000
        );
        ResponseEntity<TrackLapThresholdDto> response = restTemplate.exchange(
                "/api/v1/admin/tracks/" + trackId + "/thresholds", HttpMethod.POST,
                new HttpEntity<>(thresholdRequest, adminHeaders()), TrackLapThresholdDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().maxLastLapMs()).isEqualTo(30000);
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
        user.setLastName("Track");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
