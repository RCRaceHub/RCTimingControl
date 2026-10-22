package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The scoring system endpoints: the built-ins, create, clone, import and export, locking, validation and who may change them. */
class ScoringSystemControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/scoring-systems";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private String directorToken;
    private Long adminId;
    private String run;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        adminToken = login(Role.ADMIN);
        directorToken = login(Role.RACE_DIRECTOR);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> list(String query) {
        ResponseEntity<List> r = restTemplate.exchange(URL + query, HttpMethod.GET,
                new HttpEntity<>(headers(adminToken)), List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    void theBuiltInsAreListedByKindAndLocked() {
        List<Map<String, Object>> race = list("?kind=RACE");
        assertThat(race).extracting(m -> m.get("name")).contains("Club default", "BRCA 1/10 off-road");
        assertThat(race).allSatisfy(m -> assertThat(m.get("kind")).isEqualTo("RACE"));
        Map<String, Object> brca = race.stream().filter(m -> "BRCA 1/10 off-road".equals(m.get("name"))).findFirst().orElseThrow();
        assertThat(brca.get("builtIn")).isEqualTo(true);
        assertThat(((Map<?, ?>) brca.get("config")).get("type")).isEqualTo("BRCA");
        assertThat(((Map<?, ?>) brca.get("config")).get("finalLegs")).isEqualTo(3);

        List<Map<String, Object>> champ = list("?kind=CHAMPIONSHIP");
        Map<String, Object> champBrca = champ.stream().filter(m -> "BRCA 1/10 off-road".equals(m.get("name"))).findFirst().orElseThrow();
        assertThat((List<?>) ((Map<?, ?>) champBrca.get("config")).get("pointsScale")).hasSize(10);

        Long id = ((Number) brca.get("id")).longValue();
        assertThat(send(HttpMethod.DELETE, URL + "/" + id, null, adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.PUT, URL + "/" + id, Map.of("name", "Mine", "config", brca.get("config")), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cloneThenEditThenDelete_isAuditedAndLeavesTheBuiltInAlone() {
        Long builtInId = ((Number) list("?kind=RACE").stream()
                .filter(m -> "BRCA 1/10 off-road".equals(m.get("name"))).findFirst().orElseThrow().get("id")).longValue();

        ResponseEntity<Map> cloned = send(HttpMethod.POST, URL + "/" + builtInId + "/clone",
                Map.of("name", "Club BRCA " + run), adminToken);
        assertThat(cloned.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cloned.getBody().get("builtIn")).isEqualTo(false);
        Long id = ((Number) cloned.getBody().get("id")).longValue();

        Map<String, Object> config = Map.of("type", "BRCA", "bestHeatsCount", 3, "splitTiedPlaces", true,
                "maxRaceWindowSeconds", 390, "finalLegs", 2, "legsCounted", 2, "carsPerFinal", 10, "fillTopFirst", true);
        ResponseEntity<Map> updated = send(HttpMethod.PUT, URL + "/" + id, Map.of("name", "Club BRCA " + run, "config", config), adminToken);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) updated.getBody().get("config")).get("bestHeatsCount")).isEqualTo(3);

        assertThat(send(HttpMethod.DELETE, URL + "/" + id, null, adminToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.GET, URL + "/" + id, null, adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<Map<String, Object>> rows = jdbc.queryForList("select * from audit_log where actor_user_id = ? "
                + "and entity_type = 'scoring_system' order by id", adminId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "SCORING_SYSTEM_CLONED", "SCORING_SYSTEM_UPDATED", "SCORING_SYSTEM_DELETED");
        assertThat(rows.get(1).get("before_json").toString()).contains("\"bestHeatsCount\":2");
        assertThat(rows.get(1).get("after_json").toString()).contains("\"bestHeatsCount\":3");
    }

    @Test
    void importThenExportRoundTripsAsJsonAndYaml() {
        HttpHeaders headers = headers(adminToken);
        ResponseEntity<Map> imported = restTemplate.exchange(URL + "/import?name=Imported " + run, HttpMethod.POST,
                new HttpEntity<>("""
                        {"type":"PLACE_POINTS","bestX":4,"bestY":6,"tqBonusPoints":1,"aFinalWinnerBonusPoints":2,
                         "showOverallTable":true,"pointsScale":[{"position":1,"points":10},{"position":2,"points":7}]}""",
                        headers), Map.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(imported.getBody().get("kind")).isEqualTo("CHAMPIONSHIP");
        Long id = ((Number) imported.getBody().get("id")).longValue();

        HttpHeaders yaml = headers(adminToken);
        yaml.setAccept(List.of(MediaType.parseMediaType("application/yaml")));
        ResponseEntity<String> exported = restTemplate.exchange(URL + "/" + id + "/export", HttpMethod.GET,
                new HttpEntity<>(yaml), String.class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getBody()).contains("PLACE_POINTS").contains("bestX: 4");

        HttpHeaders yamlIn = headers(adminToken);
        yamlIn.setContentType(MediaType.parseMediaType("application/yaml"));
        ResponseEntity<Map> again = restTemplate.exchange(URL + "/import?name=Again " + run, HttpMethod.POST,
                new HttpEntity<>(exported.getBody(), yamlIn), Map.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().get("config")).isEqualTo(imported.getBody().get("config"));
    }

    @Test
    void outOfRangeOrUnknownRulesAreRefused() {
        Map<String, Object> badLegs = Map.of("type", "CLASSIC", "bestHeatsCount", 0, "splitTiedPlaces", false,
                "maxRaceWindowSeconds", 0, "finalLegs", 2, "legsCounted", 3, "carsPerFinal", 0, "fillTopFirst", false);
        assertThat(send(HttpMethod.POST, URL, Map.of("name", "Bad " + run, "config", badLegs), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> unknownType = restTemplate.exchange(URL + "/import?name=Weird " + run, HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"SCRIPT\"}", headers(adminToken)), Map.class);
        assertThat(unknownType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> lopsided = Map.of("type", "PLACE_POINTS", "bestX", 3, "tqBonusPoints", 0,
                "aFinalWinnerBonusPoints", 0, "showOverallTable", false, "pointsScale", List.of());
        ResponseEntity<Map> blank = restTemplate.exchange(URL + "/import?name={name}", HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"PLACE_POINTS\",\"pointsScale\":[]}", headers(adminToken)), Map.class, " ");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> huge = Map.of("type", "PLACE_POINTS", "tqBonusPoints", 2_000_000_000,
                "aFinalWinnerBonusPoints", 0, "showOverallTable", false, "pointsScale", List.of());
        assertThat(send(HttpMethod.POST, URL, Map.of("name", "Huge " + run, "config", huge), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(send(HttpMethod.POST, URL, Map.of("name", "Lopsided " + run, "config", lopsided), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRaceDirectorCanReadButNotChange() {
        assertThat(restTemplate.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers(directorToken)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> config = Map.of("type", "CLASSIC", "bestHeatsCount", 0, "splitTiedPlaces", false,
                "maxRaceWindowSeconds", 0, "finalLegs", 1, "legsCounted", 0, "carsPerFinal", 0, "fillTopFirst", false);
        assertThat(send(HttpMethod.POST, URL, Map.of("name", "Dir " + run, "config", config), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(URL + "/import?name=Dir " + run, HttpMethod.POST,
                new HttpEntity<>("{}", headers(directorToken)), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aTakenNameIsAConflict() {
        Map<String, Object> config = Map.of("type", "CLASSIC", "bestHeatsCount", 0, "splitTiedPlaces", false,
                "maxRaceWindowSeconds", 0, "finalLegs", 1, "legsCounted", 0, "carsPerFinal", 0, "fillTopFirst", false);
        assertThat(send(HttpMethod.POST, URL, Map.of("name", "Club default", "config", config), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.POST, URL, Map.of("name", "CLUB DEFAULT", "config", config), adminToken).getStatusCode())
                .as("names match without regard to case").isEqualTo(HttpStatus.CONFLICT);
    }

    // --- helpers ---

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> send(HttpMethod method, String url, Object body, String token) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private String login(Role role) {
        String email = role.name().toLowerCase() + "-scoring-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName(role.name());
        user.setLastName("Scoring");
        user.setRoles(Set.of(role));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        User saved = userRepository.save(user);
        if (role == Role.ADMIN) {
            adminId = saved.getId();
        }
        ResponseEntity<AuthResponse> r = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().accessToken();
    }
}
