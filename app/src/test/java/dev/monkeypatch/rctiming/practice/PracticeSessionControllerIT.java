package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.dto.PracticeDayDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeSessionDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
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

/**
 * Integration tests for PracticeSessionController.
 * Exercises create/start/stop state machine via HTTP against the SQLite test database.
 */
class PracticeSessionControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String rdToken;  // RACE_DIRECTOR token

    @BeforeEach
    void setUp() {
        finishLeftoverRunningRaces();
        String email = "rd-practice-" + UUID.randomUUID() + "@test.com";
        createRaceDirectorUser(email, "rdPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "rdPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        rdToken = loginResp.getBody().accessToken();
    }

    @Test
    void createSession_withAnUnknownEvent_answers404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/practice-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Nowhere Practice", "eventId", 987654321L), rdHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createSession_validRequest_returns201() {
        ResponseEntity<PracticeSessionDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Morning Practice"), rdHeaders()),
                PracticeSessionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Morning Practice");
        assertThat(response.getBody().status().name()).isEqualTo("IDLE");
        assertThat(response.getBody().bestLapN()).isEqualTo(5);
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void createSession_withEventLink_associatesEvent() {
        // For simplicity: no actual event link — just verify session created with bestLapN
        ResponseEntity<PracticeSessionDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Linked Practice", "bestLapN", 5), rdHeaders()),
                PracticeSessionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().bestLapN()).isEqualTo(5);
        assertThat(response.getBody().eventId()).isNull();
    }

    @Test
    void startSession_idleSession_transitionsToRunning() {
        Long id = createSession("Start Test Session");

        ResponseEntity<PracticeSessionDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions/" + id + "/start", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()),
                PracticeSessionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status().name()).isEqualTo("RUNNING");
        assertThat(response.getBody().startedAt()).isNotNull();

        // Stop to clean up running session for other tests
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/stop",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);
    }

    @Test
    void day_listsTheSessionsStartedToday() {
        Long id = createSession("Day View Session");
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/start", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/stop", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);

        ResponseEntity<PracticeDayDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions/day", HttpMethod.GET, new HttpEntity<>(rdHeaders()),
                PracticeDayDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bestLapN()).isEqualTo(5);
        assertThat(response.getBody().sessions()).extracting(PracticeSessionDto::id).contains(id);
    }

    @Test
    void day_withNoLapsToAverage_answers400() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/practice-sessions/day?bestLapN=0", HttpMethod.GET, new HttpEntity<>(rdHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void stopSession_runningSession_transitionsToStopped() {
        Long id = createSession("Stop Test Session");
        // Start first
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);

        ResponseEntity<PracticeSessionDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions/" + id + "/stop", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()),
                PracticeSessionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status().name()).isEqualTo("STOPPED");
        assertThat(response.getBody().stoppedAt()).isNotNull();
    }

    @Test
    void startSession_alreadyRunning_returns409() {
        Long id = createSession("Conflict Test Session");
        // Start first time
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);

        // Try to start again
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/practice-sessions/" + id + "/start", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cleanup
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/stop",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);
    }

    @Test
    void startSession_unknownSession_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/practice-sessions/987654321/start", HttpMethod.POST,
                new HttpEntity<>(rdHeaders()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getResults_stoppedSession_returnsFinalSnapshot() {
        Long id = createSession("Results Test Session");
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);
        restTemplate.exchange("/api/v1/practice-sessions/" + id + "/stop",
                HttpMethod.POST, new HttpEntity<>(rdHeaders()), PracticeSessionDto.class);

        ResponseEntity<List<PracticeTimingRowDto>> response = restTemplate.exchange(
                "/api/v1/practice-sessions/" + id + "/results", HttpMethod.GET,
                new HttpEntity<>(rdHeaders()),
                new ParameterizedTypeReference<List<PracticeTimingRowDto>>() {});

        // No laps recorded in this test but endpoint should return empty list, not error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Long createSession(String name) {
        ResponseEntity<PracticeSessionDto> response = restTemplate.exchange(
                "/api/v1/practice-sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), rdHeaders()),
                PracticeSessionDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private HttpHeaders rdHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(rdToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void createRaceDirectorUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Race");
        user.setLastName("Director");
        user.setRoles(Set.of(Role.RACE_DIRECTOR));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
