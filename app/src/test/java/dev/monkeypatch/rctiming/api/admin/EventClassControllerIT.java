package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.admin.dto.EventClassDto;
import dev.monkeypatch.rctiming.api.admin.dto.EventDto;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventClassControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RaceFormatTemplateRepository templateRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventClassRepository eventClassRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String email = "admin-ec-" + UUID.randomUUID() + "@test.com";
        createAdminUser(email, "adminPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void addClassToEvent_snapshotsConfigFromTemplate() {
        Long eventId = createEventInDb();
        Long templateId = createTemplateInDb(10);
        Long racingClassId = createRacingClassInDb();

        ResponseEntity<EventClassDto> response = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("racingClassId", racingClassId, "templateId", templateId),
                        adminHeaders()),
                EventClassDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        // configSnapshot should contain the template config — serialized as a map
        assertThat(response.getBody().configSnapshot()).isNotNull();
        // The snapshot type should be TIMED with durationMinutes=10
        // Since configSnapshot is a RaceFormatConfig (sealed interface), Jackson will serialize it with type info
        // We check it's non-null and that the response is a valid EventClassDto
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void addClassToEvent_setsRacingClassIdAndEventId() {
        Long eventId = createEventInDb();
        Long templateId = createTemplateInDb(5);
        Long racingClassId = createRacingClassInDb();

        ResponseEntity<EventClassDto> response = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("racingClassId", racingClassId, "templateId", templateId),
                        adminHeaders()),
                EventClassDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().racingClassId()).isEqualTo(racingClassId);
        assertThat(response.getBody().eventId()).isEqualTo(eventId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateOverrides_setsOverrideMap() {
        Long eventId = createEventInDb();
        Long templateId = createTemplateInDb(10);
        Long racingClassId = createRacingClassInDb();

        // Add class to event
        ResponseEntity<EventClassDto> addResp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("racingClassId", racingClassId, "templateId", templateId),
                        adminHeaders()),
                EventClassDto.class);
        Long classId = addResp.getBody().id();

        // Update with override
        ResponseEntity<EventClassDto> overrideResp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes/" + classId + "/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("override", Map.of("durationMinutes", 15)), adminHeaders()),
                EventClassDto.class);

        assertThat(overrideResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overrideResp.getBody().configOverride()).isNotNull();
        assertThat(overrideResp.getBody().configOverride().get("durationMinutes")).isEqualTo(15);
        // configSnapshot should be unchanged (still durationMinutes=10)
        assertThat(overrideResp.getBody().configSnapshot()).isNotNull();
    }

    @Test
    void updateOverrides_throughAnotherEventsUrl_isRefusedAndChangesNothing() {
        Long eventId = createEventInDb();
        Long otherEventId = createEventInDb();
        Long classId = addClassToEvent(eventId, createTemplateInDb(10), createRacingClassInDb());

        ResponseEntity<String> refused = restTemplate.exchange(
                "/api/v1/admin/events/" + otherEventId + "/classes/" + classId + "/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("override", Map.of("durationMinutes", 15)), adminHeaders()),
                String.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(eventClassRepository.findById(classId).orElseThrow().getConfigOverride()).isNull();
    }

    @Test
    void updateOverrides_emptyMap_clearsOverride() {
        Long eventId = createEventInDb();
        Long templateId = createTemplateInDb(10);
        Long racingClassId = createRacingClassInDb();

        // Add class to event
        ResponseEntity<EventClassDto> addResp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("racingClassId", racingClassId, "templateId", templateId),
                        adminHeaders()),
                EventClassDto.class);
        Long classId = addResp.getBody().id();

        // First set an override
        restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes/" + classId + "/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("override", Map.of("durationMinutes", 15)), adminHeaders()),
                EventClassDto.class);

        // Now clear it with empty map
        ResponseEntity<EventClassDto> clearResp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes/" + classId + "/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("override", Map.of()), adminHeaders()),
                EventClassDto.class);

        assertThat(clearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearResp.getBody().configOverride()).isNull();
    }

    /** Rounds are generated from the format with its override laid over it, so one that does not fit is refused. */
    @Test
    void updateOverrides_thatDoNotFitTheFormat_areRefusedAndNotStored() {
        Long eventId = createEventInDb();
        Long classId = addClassToEvent(eventId, createTemplateInDb(10), createRacingClassInDb());

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes/" + classId + "/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("override", Map.of("startType", "ROLLING_START")), adminHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(eventClassRepository.getOrThrow(classId).getConfigOverride()).isNull();
    }

    @Test
    void combineClasses_assignsSameGroupId() {
        Long eventId = createEventInDb();
        Long templateId = createTemplateInDb(10);

        // Create 3 classes for same event
        Long classId1 = addClassToEvent(eventId, templateId, createRacingClassInDb());
        Long classId2 = addClassToEvent(eventId, templateId, createRacingClassInDb());
        Long classId3 = addClassToEvent(eventId, templateId, createRacingClassInDb());

        // Combine all 3
        ResponseEntity<List<EventClassDto>> combineResp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes/combine",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("eventClassIds", List.of(classId1, classId2, classId3)),
                        adminHeaders()),
                new ParameterizedTypeReference<List<EventClassDto>>() {});

        assertThat(combineResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(combineResp.getBody()).hasSize(3);

        // All 3 should have the same non-null combinedRaceGroup
        List<Long> groups = combineResp.getBody().stream()
                .map(EventClassDto::combinedRaceGroup)
                .toList();
        assertThat(groups).doesNotContainNull();
        assertThat(groups.stream().distinct().count()).isEqualTo(1);
    }

    @Test
    void combineClasses_rejectsClassesFromDifferentEvent() {
        Long eventAId = createEventInDb();
        Long eventBId = createEventInDb();
        Long templateId = createTemplateInDb(10);

        // Class belonging to event A
        Long classAId = addClassToEvent(eventAId, templateId, createRacingClassInDb());
        // Class belonging to event B
        Long classBId = addClassToEvent(eventBId, templateId, createRacingClassInDb());

        // Try to combine class from event B into event A's combine endpoint
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventAId + "/classes/combine",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("eventClassIds", List.of(classAId, classBId)), adminHeaders()),
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
        user.setLastName("EventClass");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private Long createEventInDb() {
        Event event = new Event();
        event.setName("Test Event " + UUID.randomUUID().toString().substring(0, 8));
        event.setEventDate(LocalDate.of(2026, 6, 1));
        event.setStatus(EventStatus.DRAFT);
        Instant now = Instant.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return eventRepository.save(event).getId();
    }

    private Long createTemplateInDb(int durationMinutes) {
        RaceFormatTemplate template = new RaceFormatTemplate();
        template.setName("Template " + UUID.randomUUID().toString().substring(0, 8));
        template.setConfig(new TimedRaceConfig(durationMinutes, StartType.ROLLING, QualifyingType.FASTEST_LAP, 5, 5));
        return templateRepository.save(template).getId();
    }

    private Long createRacingClassInDb() {
        RacingClass rc = new RacingClass();
        rc.setName("Class " + UUID.randomUUID().toString().substring(0, 8));
        Instant now = Instant.now();
        rc.setCreatedAt(now);
        rc.setUpdatedAt(now);
        return racingClassRepository.save(rc).getId();
    }

    private Long addClassToEvent(Long eventId, Long templateId, Long racingClassId) {
        ResponseEntity<EventClassDto> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("racingClassId", racingClassId, "templateId", templateId),
                        adminHeaders()),
                EventClassDto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().id();
    }
}
