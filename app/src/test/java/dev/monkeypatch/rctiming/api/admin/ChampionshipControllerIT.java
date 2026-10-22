package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChampionshipControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    CompetitorRepository competitorRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenService jwtTokenService;

    private String adminToken;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        String email = "admin-champ-" + UUID.randomUUID() + "@test.com";
        adminUserId = createAdminUser(email, "adminPass123", Set.of(Role.ADMIN));
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        adminToken = loginResp.getBody().accessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createChampionship_asAdmin_returns201WithDefaults() {
        Map<String, Object> body = Map.of(
                "name", "National Championship 2026",
                "tqBonusPoints", 0,
                "afinalWinnerBonusPoints", 0
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
        assertEquals("National Championship 2026", response.getBody().get("name"));
        assertEquals(0, response.getBody().get("tqBonusPoints"));
        assertEquals(0, response.getBody().get("afinalWinnerBonusPoints"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createChampionship_withBonusAndBestXY_persistsAllFields() {
        Map<String, Object> createBody = Map.of(
                "name", "Club Series 2026",
                "bestXFromYX", 7,
                "bestXFromYY", 10,
                "tqBonusPoints", 3,
                "afinalWinnerBonusPoints", 5
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());

        Long id = ((Number) createResp.getBody().get("id")).longValue();

        ResponseEntity<Map> getResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Map<String, Object> detail = getResp.getBody();
        assertEquals(7, detail.get("bestXFromYX"));
        assertEquals(10, detail.get("bestXFromYY"));
        assertEquals(3, detail.get("tqBonusPoints"));
        assertEquals(5, detail.get("afinalWinnerBonusPoints"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listChampionships_returnsAllCreated() {
        createChampionship("Series Alpha");
        createChampionship("Series Beta");

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 2);
        List<String> names = ((List<Map<String, Object>>) response.getBody()).stream()
                .map(c -> (String) c.get("name"))
                .toList();
        assertTrue(names.contains("Series Alpha"));
        assertTrue(names.contains("Series Beta"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addClass_linksRacingClass_returns201() {
        Long champId = createChampionship("RC Buggy Championship");

        // Create a racing class
        String className = "Stock-Buggy-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> classResp = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", className), adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, classResp.getStatusCode());
        Long racingClassId = ((Number) classResp.getBody().get("id")).longValue();

        // Add class to championship
        Map<String, Object> addClassBody = Map.of("racingClassId", racingClassId);
        ResponseEntity<Map> addResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/classes", HttpMethod.POST,
                new HttpEntity<>(addClassBody, adminHeaders()), Map.class);

        assertEquals(HttpStatus.CREATED, addResp.getStatusCode());
        assertNotNull(addResp.getBody().get("id"));
        assertEquals(racingClassId.intValue(), ((Number) addResp.getBody().get("racingClassId")).intValue());

        // Verify via detail GET
        ResponseEntity<Map> detailResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        List<Map<String, Object>> classes = (List<Map<String, Object>>) detailResp.getBody().get("classes");
        assertNotNull(classes);
        assertEquals(1, classes.size());
        assertEquals(racingClassId.intValue(), ((Number) classes.get(0).get("racingClassId")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addClass_duplicateRacingClass_returns409() {
        Long champId = createChampionship("Duplicate Class Championship");

        String className = "Truggy-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> classResp = restTemplate.exchange(
                "/api/v1/admin/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", className), adminHeaders()), Map.class);
        Long racingClassId = ((Number) classResp.getBody().get("id")).longValue();

        Map<String, Object> addClassBody = Map.of("racingClassId", racingClassId);

        // First add — should succeed
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/classes", HttpMethod.POST,
                new HttpEntity<>(addClassBody, adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        // Second add same racing class — should conflict
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/classes", HttpMethod.POST,
                new HttpEntity<>(addClassBody, adminHeaders()), String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void linkEvent_withRoundNumber_returns201() {
        Long champId = createChampionship("Event Link Championship");
        Long eventId = createEvent("Round 1 Event");

        Map<String, Object> linkBody = Map.of("eventId", eventId, "roundNumber", 1);
        ResponseEntity<Map> linkResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(linkBody, adminHeaders()), Map.class);

        assertEquals(HttpStatus.CREATED, linkResp.getStatusCode());
        assertNotNull(linkResp.getBody().get("id"));
        assertEquals(1, linkResp.getBody().get("roundNumber"));

        // Verify via detail GET
        ResponseEntity<Map> detailResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        List<Map<String, Object>> events = (List<Map<String, Object>>) detailResp.getBody().get("events");
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).get("roundNumber"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void linkEvent_duplicateRoundNumber_returns409() {
        Long champId = createChampionship("Duplicate Round Championship");
        Long event1Id = createEvent("Round 1 Event A");
        Long event2Id = createEvent("Round 1 Event B");

        // Link event1 to round 1
        Map<String, Object> link1 = Map.of("eventId", event1Id, "roundNumber", 1);
        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(link1, adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        // Link event2 to round 1 — same round number, should conflict
        Map<String, Object> link2 = Map.of("eventId", event2Id, "roundNumber", 1);
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(link2, adminHeaders()), String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacePointsScale_writesAllEntries_returnsSortedList() {
        Long champId = createChampionship("Points Scale Championship");

        // PUT 10-entry ROAR preset
        List<Map<String, Object>> entries10 = new ArrayList<>();
        int[] points10 = {20, 17, 15, 13, 12, 11, 10, 9, 8, 7};
        for (int i = 0; i < 10; i++) {
            entries10.add(Map.of("position", i + 1, "points", points10[i]));
        }
        Map<String, Object> putBody10 = Map.of("entries", entries10);
        ResponseEntity<List> putResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/points-scale", HttpMethod.PUT,
                new HttpEntity<>(putBody10, adminHeaders()), List.class);

        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        assertNotNull(putResp.getBody());
        assertEquals(10, putResp.getBody().size());
        Map<String, Object> firstEntry = (Map<String, Object>) putResp.getBody().get(0);
        assertEquals(1, firstEntry.get("position"));
        assertEquals(20, firstEntry.get("points"));

        // Replace with 5 entries
        List<Map<String, Object>> entries5 = new ArrayList<>();
        int[] points5 = {10, 8, 6, 4, 2};
        for (int i = 0; i < 5; i++) {
            entries5.add(Map.of("position", i + 1, "points", points5[i]));
        }
        Map<String, Object> putBody5 = Map.of("entries", entries5);
        restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/points-scale", HttpMethod.PUT,
                new HttpEntity<>(putBody5, adminHeaders()), List.class);

        // GET detail should show only 5 entries
        ResponseEntity<Map> detailResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        List<Map<String, Object>> scale = (List<Map<String, Object>>) detailResp.getBody().get("pointsScale");
        assertNotNull(scale);
        assertEquals(5, scale.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createExclusion_asAdmin_returns201WithAudit() {
        Long champId = createChampionship("Exclusion Audit Championship");

        // Drivers are competitors (L5); this one has no login
        Long driverId = createCompetitor("Exclusion Driver");

        // Create event
        Long eventId = createEvent("Round 2 Event");

        // Link event to championship
        restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("eventId", eventId, "roundNumber", 2), adminHeaders()), Map.class);

        // Create exclusion
        Map<String, Object> exclusionBody = Map.of(
                "driverId", driverId,
                "eventId", eventId,
                "reason", "missed round 2"
        );
        ResponseEntity<Map> exclusionResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions", HttpMethod.POST,
                new HttpEntity<>(exclusionBody, adminHeaders()), Map.class);

        assertEquals(HttpStatus.CREATED, exclusionResp.getStatusCode());
        assertNotNull(exclusionResp.getBody().get("id"));
        assertEquals(driverId.intValue(), ((Number) exclusionResp.getBody().get("driverId")).intValue());
        assertNotNull(exclusionResp.getBody().get("createdBy"));
        // createdBy must be the acting admin, NOT from request body
        assertEquals(adminUserId.intValue(),
                ((Number) exclusionResp.getBody().get("createdBy")).intValue());
        assertNotNull(exclusionResp.getBody().get("createdAt"));
        assertEquals("missed round 2", exclusionResp.getBody().get("reason"));
        // The official is named for the screen, which only has the user id otherwise
        assertEquals("Admin User", exclusionResp.getBody().get("createdByName"));

        // GET exclusions returns 1 row
        ResponseEntity<List> listResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertEquals(1, listResp.getBody().size());
        assertEquals("Admin User", ((Map<?, ?>) listResp.getBody().get(0)).get("createdByName"));
    }

    @Test
    void createExclusion_forLoginUserIdThatIsNotACompetitor_returns404() {
        Long champId = createChampionship("Exclusion Unknown Competitor Championship");
        Long eventId = createEvent("Unknown Competitor Event");
        restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("eventId", eventId, "roundNumber", 1), adminHeaders()), Map.class);

        // A competitor id that does not exist (well past any id this run creates)
        Map<String, Object> body = Map.of("driverId", 9_999_999L, "eventId", eventId, "reason", "test");
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), Map.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCompetitors_includesCompetitorWithNoLogin() {
        String name = "Walk-in " + UUID.randomUUID();
        Long competitorId = createCompetitor(name);

        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/admin/competitors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<String, Object>> rows = resp.getBody();
        assertTrue(rows.stream().anyMatch(r ->
                ((Number) r.get("id")).longValue() == competitorId && name.equals(r.get("displayName"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteExclusion_removesRow_returns204() {
        Long champId = createChampionship("Delete Exclusion Championship");

        Long driverId = createCompetitor("Delete Exclusion Driver");
        Long eventId = createEvent("Delete Round Event");

        restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/events", HttpMethod.POST,
                new HttpEntity<>(Map.of("eventId", eventId, "roundNumber", 3), adminHeaders()), Map.class);

        Map<String, Object> exclusionBody = Map.of("driverId", driverId, "eventId", eventId, "reason", "test");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions", HttpMethod.POST,
                new HttpEntity<>(exclusionBody, adminHeaders()), Map.class);
        Long exclusionId = ((Number) createResp.getBody().get("id")).longValue();

        // DELETE exclusion
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions/" + exclusionId,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // GET exclusions returns empty list
        ResponseEntity<List> listResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/exclusions", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);
        assertEquals(0, listResp.getBody().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getStandings_returnsEmptyListInPhase3() {
        Long champId = createChampionship("Standings Stub Championship");

        ResponseEntity<List> standingsResp = restTemplate.exchange(
                "/api/v1/admin/championships/" + champId + "/standings", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);

        assertEquals(HttpStatus.OK, standingsResp.getStatusCode());
        assertNotNull(standingsResp.getBody());
        assertEquals(0, standingsResp.getBody().size());
    }

    @Test
    void createChampionship_missingName_returns400() {
        Map<String, Object> body = Map.of(
                "tqBonusPoints", 0,
                "afinalWinnerBonusPoints", 0
        );
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void allEndpoints_withNoOfficialRole_return403() {
        // Accounts with no official role can no longer sign in; a token minted for one is still refused
        User noRoles = new User();
        noRoles.setEmail("noroles-champ-" + UUID.randomUUID() + "@test.com");
        noRoles.setPasswordHash(passwordEncoder.encode("password123"));
        noRoles.setFirstName("No");
        noRoles.setLastName("Roles");
        noRoles.setRoles(java.util.Set.of());
        noRoles.setCreatedAt(java.time.Instant.now());
        noRoles.setUpdatedAt(java.time.Instant.now());
        String racerToken = jwtTokenService.generateAccessToken(userRepository.save(noRoles));
        HttpHeaders racerHeaders = headersFor(racerToken);
        Map<String, Object> body = Map.of(
                "name", "test",
                "tqBonusPoints", 0,
                "afinalWinnerBonusPoints", 0
        );

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.POST,
                new HttpEntity<>(body, racerHeaders), String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // --- helpers ---

    private Long createChampionship(String name) {
        Map<String, Object> body = Map.of(
                "name", name,
                "tqBonusPoints", 0,
                "afinalWinnerBonusPoints", 0
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/admin/championships", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private Long createEvent(String name) {
        Event event = new Event();
        event.setName(name);
        event.setEventDate(LocalDate.now().plusDays(30));
        event.setStatus(EventStatus.DRAFT);
        Instant now = Instant.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return eventRepository.save(event).getId();
    }

    private Long createCompetitor(String displayName) {
        Competitor competitor = new Competitor();
        competitor.setDisplayName(displayName);
        Instant now = Instant.now();
        competitor.setCreatedAt(now);
        competitor.setUpdatedAt(now);
        return competitorRepository.save(competitor).getId();
    }

    private Long createAdminUser(String email, String password, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getId();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders headersFor(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
