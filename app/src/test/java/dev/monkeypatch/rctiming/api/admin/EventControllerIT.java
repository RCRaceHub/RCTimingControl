package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.admin.dto.EventDto;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.track.Track;
import dev.monkeypatch.rctiming.domain.track.TrackRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.query.event.AdminEventListDto;
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

class EventControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-event-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    void createEvent_asAdmin_returns201AndDraftStatus() {
        ResponseEntity<EventDto> response = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Spring Meeting 2026", "eventDate", "2026-05-10"),
                        adminHeaders()),
                EventDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Spring Meeting 2026");
        assertThat(response.getBody().status().name()).isEqualTo("DRAFT");
        assertThat(response.getBody().trackId()).isNull();
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void createEvent_withTrackId_returns201WithTrack() {
        Long trackId = createTrackInDb();

        ResponseEntity<EventDto> response = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Track Event", "eventDate", "2026-06-01", "trackId", trackId),
                        adminHeaders()),
                EventDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().trackId()).isEqualTo(trackId);
    }

    @Test
    void listEvents_returnsAllWithTrackName() {
        Long trackId = createTrackInDb();

        // Create one event with track, one without
        restTemplate.exchange("/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Event With Track", "eventDate", "2026-07-01", "trackId", trackId),
                        adminHeaders()), EventDto.class);
        restTemplate.exchange("/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Event No Track", "eventDate", "2026-07-02"),
                        adminHeaders()), EventDto.class);

        ResponseEntity<List<AdminEventListDto>> response = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<List<AdminEventListDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<String> names = response.getBody().stream().map(AdminEventListDto::name).toList();
        assertThat(names).contains("Event With Track", "Event No Track");

        // Event with track should have trackName populated
        AdminEventListDto withTrack = response.getBody().stream()
                .filter(e -> "Event With Track".equals(e.name()))
                .findFirst()
                .orElseThrow();
        assertThat(withTrack.trackName()).isNotNull();
    }

    @Test
    void updateEvent_modifiesFields() {
        ResponseEntity<EventDto> created = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Old Name", "eventDate", "2026-08-01"),
                        adminHeaders()), EventDto.class);
        Long id = created.getBody().id();

        ResponseEntity<EventDto> updated = restTemplate.exchange(
                "/api/v1/admin/events/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "New Name", "eventDate", "2026-08-15"),
                        adminHeaders()), EventDto.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("New Name");
    }

    @Test
    void transitionEvent_validTransition_returns200() {
        ResponseEntity<EventDto> created = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Transition Event", "eventDate", "2026-09-01"),
                        adminHeaders()), EventDto.class);
        Long id = created.getBody().id();

        ResponseEntity<EventDto> transitioned = restTemplate.exchange(
                "/api/v1/admin/events/" + id + "/transition", HttpMethod.POST,
                new HttpEntity<>(Map.of("targetStatus", "PUBLISHED"), adminHeaders()),
                EventDto.class);

        assertThat(transitioned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transitioned.getBody().status().name()).isEqualTo("PUBLISHED");
    }

    @Test
    void transitionEvent_invalidTransition_returns409() {
        ResponseEntity<EventDto> created = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Invalid Transition Event", "eventDate", "2026-09-10"),
                        adminHeaders()), EventDto.class);
        Long id = created.getBody().id();

        // DRAFT -> IN_PROGRESS is invalid (must go DRAFT -> PUBLISHED -> OPEN -> ...)
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + id + "/transition", HttpMethod.POST,
                new HttpEntity<>(Map.of("targetStatus", "IN_PROGRESS"), adminHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void transitionEvent_fromCompletedToAnyState_returns409() {
        ResponseEntity<EventDto> created = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Complete Event", "eventDate", "2026-10-01"),
                        adminHeaders()), EventDto.class);
        Long id = created.getBody().id();

        // Walk through all valid transitions to reach COMPLETED
        for (String target : new String[]{"PUBLISHED", "OPEN", "ENTRIES_CLOSED", "IN_PROGRESS", "COMPLETED"}) {
            restTemplate.exchange("/api/v1/admin/events/" + id + "/transition", HttpMethod.POST,
                    new HttpEntity<>(Map.of("targetStatus", target), adminHeaders()), EventDto.class);
        }

        // COMPLETED -> any state is invalid
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + id + "/transition", HttpMethod.POST,
                new HttpEntity<>(Map.of("targetStatus", "PUBLISHED"), adminHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createEvent_missingName_returns400() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("eventDate", "2026-05-10"), adminHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        user.setLastName("Event");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private Long createTrackInDb() {
        Track track = new Track();
        track.setName("Test Track " + UUID.randomUUID().toString().substring(0, 8));
        Instant now = Instant.now();
        track.setCreatedAt(now);
        track.setUpdatedAt(now);
        return trackRepository.save(track).getId();
    }
}
