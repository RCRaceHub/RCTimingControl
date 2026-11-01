package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The screen manager end to end (#708): adding screens by host and port, the list with live status, playlists, push,
 * drift and re-push, controls, temporary pages, tokens, and who may do what, against fake agents.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ScreenManagerIT extends AbstractIntegrationTest {

    private static final String SCREENS = "/api/v1/admin/screens";
    private static final String PLAYLISTS = "/api/v1/admin/screen-playlists";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScreenMonitor monitor;
    @Autowired ScreenRepository screenRepository;
    @Autowired ScreenControlService control;

    private String directorToken;
    private String refereeToken;
    private String suffix;
    private FakeScreen open;
    private FakeScreen locked;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        directorToken = login(Role.RACE_DIRECTOR);
        refereeToken = login(Role.REFEREE);
        open = new FakeScreen("it-open-" + suffix, null);
        locked = new FakeScreen("it-locked-" + suffix, "secret");
    }

    @AfterEach
    void tearDown() {
        open.close();
        locked.close();
        jdbc.update("delete from display_screens where screen_id like 'it-%'");
        jdbc.update("delete from display_playlists where name like 'it-%'");
    }

    @Test
    void aScreenAddedByHandIsListedWithItsLiveStatus() {
        Map added = add(open, null);

        assertThat(added.get("screenId")).isEqualTo("it-open-" + suffix);
        assertThat(added.get("name")).isEqualTo("Screen it-open-" + suffix);
        assertThat(added.get("hasToken")).isEqualTo(false);
        Long id = ((Number) added.get("id")).longValue();
        monitor.poll(screenById(id));
        Map listed = screenInList(id);
        assertThat(listed.get("connection")).isEqualTo("ONLINE");
        assertThat(((Map) listed.get("live")).get("state")).isEqualTo("idle");
        assertThat(listed).doesNotContainKey("apiToken");
    }

    @Test
    void addingTheSameScreenAgainUpdatesItInsteadOfDuplicating() {
        Map first = add(open, null);
        Map second = add(open, null);

        assertThat(second.get("id")).isEqualTo(first.get("id"));
    }

    @Test
    void aScreenThatCannotBeReachedIsNotAdded() {
        int port = open.port();
        open.close();

        ResponseEntity<Map> r = send(HttpMethod.POST, SCREENS, Map.of("host", "127.0.0.1", "port", port), directorToken);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void aBadHostIsRefused() {
        assertThat(send(HttpMethod.POST, SCREENS, Map.of("host", "http://pi", "port", 8080), directorToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aPlaylistIsPushedAndAChangeMadeElsewhereShowsAsDriftUntilItIsPushedAgain() {
        Long id = ((Number) add(open, null).get("id")).longValue();
        Long playlistId = createPlaylist("it-pit-" + suffix);

        List<Map> pushed = (List<Map>) send(HttpMethod.POST, SCREENS + "/push",
                Map.of("playlistId", playlistId, "screenIds", List.of(id)), directorToken, List.class).getBody();

        assertThat(pushed).singleElement().satisfies(r -> assertThat(r.get("ok")).isEqualTo(true));
        assertThat(open.lastBody()).contains("rctc:playlist-" + playlistId).contains("\"label\":\"Results\"");
        Map afterPush = screenInList(id);
        assertThat(afterPush.get("drifted")).isEqualTo(false);
        assertThat(afterPush.get("playlistName")).isEqualTo("it-pit-" + suffix);
        assertThat(((Number) afterPush.get("pushedRevision")).longValue()).isEqualTo(2L);

        open.changeElsewhere();
        monitor.poll(screenById(id));
        assertThat(screenInList(id).get("drifted")).isEqualTo(true);

        List<Map> again = (List<Map>) send(HttpMethod.POST, SCREENS + "/" + id + "/repush", null, directorToken,
                List.class).getBody();
        assertThat(again).singleElement().satisfies(r -> assertThat(r.get("ok")).isEqualTo(true));
        assertThat(screenInList(id).get("drifted")).isEqualTo(false);
    }

    @Test
    void aScreenThatNeedsATokenReportsItUntilOneIsSet() {
        Long id = ((Number) add(locked, null).get("id")).longValue();
        Long playlistId = createPlaylist("it-token-" + suffix);

        List<Map> refused = (List<Map>) send(HttpMethod.POST, SCREENS + "/push",
                Map.of("playlistId", playlistId, "screenIds", List.of(id)), directorToken, List.class).getBody();
        assertThat(refused).singleElement().satisfies(r -> {
            assertThat(r.get("ok")).isEqualTo(false);
            assertThat((String) r.get("error")).contains("token");
        });

        assertThat(send(HttpMethod.PUT, SCREENS + "/token", Map.of("screenIds", List.of(id), "token", "secret"),
                directorToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map> accepted = (List<Map>) send(HttpMethod.POST, SCREENS + "/push",
                Map.of("playlistId", playlistId, "screenIds", List.of(id)), directorToken, List.class).getBody();
        assertThat(accepted).singleElement().satisfies(r -> assertThat(r.get("ok")).isEqualTo(true));
        assertThat(screenInList(id).get("hasToken")).isEqualTo(true);
        String stored = jdbc.queryForObject("select api_token from display_screens where id = ?", String.class, id);
        assertThat(stored).isNotEqualTo("secret");
    }

    @Test
    void controlsAndTemporaryPagesGoToEveryScreenAndAFailureDoesNotStopTheRest() {
        Long a = ((Number) add(open, null).get("id")).longValue();
        Long b = ((Number) add(locked, "secret").get("id")).longValue();

        List<Map> paused = (List<Map>) send(HttpMethod.POST, SCREENS + "/control/pause",
                Map.of("screenIds", List.of(a, b)), directorToken, List.class).getBody();
        assertThat(paused).allSatisfy(r -> assertThat(r.get("ok")).isEqualTo(true));
        assertThat(open.calls()).contains("POST /control/pause");
        assertThat(locked.calls()).contains("POST /control/pause (auth)");

        List<Map> shown = (List<Map>) send(HttpMethod.POST, SCREENS + "/show",
                Map.of("screenIds", List.of(a, b), "url", "http://10.0.0.2:8080/boards/results", "seconds", 30),
                directorToken, List.class).getBody();
        assertThat(shown).allSatisfy(r -> assertThat(r.get("ok")).isEqualTo(true));
        monitor.poll(screenById(a));
        assertThat(((Map) screenInList(a).get("live")).get("overrideUrl")).isEqualTo("http://10.0.0.2:8080/boards/results");

        List<Map> goto99 = (List<Map>) send(HttpMethod.POST, SCREENS + "/control/goto",
                Map.of("screenIds", List.of(a), "page", 100), directorToken, List.class).getBody();
        assertThat(goto99).singleElement().satisfies(r -> assertThat((String) r.get("error")).isEqualTo("index out of range"));

        List<Map> cleared = (List<Map>) send(HttpMethod.POST, SCREENS + "/show/clear", Map.of("screenIds", List.of(a)),
                directorToken, List.class).getBody();
        assertThat(cleared).singleElement().satisfies(r -> assertThat(r.get("ok")).isEqualTo(true));
    }

    @Test
    void aPageOnAnotherHostIsRefusedInAPlaylistAndAsATemporaryPage() {
        Long a = ((Number) add(open, null).get("id")).longValue();
        Map<String, Object> body = Map.of("name", "it-away-" + suffix, "defaultDuration", 30,
                "items", List.of(Map.of("url", "http://evil.example/boards/results")));

        assertThat(send(HttpMethod.POST, PLAYLISTS, body, directorToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, SCREENS + "/show", Map.of("screenIds", List.of(a), "url", "http://evil.example/x"),
                directorToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTemporaryPageMustBeAFullAddress() {
        Long a = ((Number) add(open, null).get("id")).longValue();

        assertThat(send(HttpMethod.POST, SCREENS + "/show", Map.of("screenIds", List.of(a), "url", "localhost/boards"),
                directorToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, SCREENS + "/control/explode", Map.of("screenIds", List.of(a)), directorToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void playlistsAreCreatedChangedAndDeletedAndNamesAreUnique() {
        Long id = createPlaylist("it-list-" + suffix);

        assertThat(send(HttpMethod.POST, PLAYLISTS, playlistBody("it-list-" + suffix), directorToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        Map body = new HashMap(playlistBody("it-list-" + suffix));
        body.put("items", List.of());
        assertThat(send(HttpMethod.PUT, PLAYLISTS + "/" + id, body, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.DELETE, PLAYLISTS + "/" + id, null, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.DELETE, PLAYLISTS + "/" + id, null, directorToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRefereeCanReadButNotChangeOrControlAnything() {
        assertThat(send(HttpMethod.GET, SCREENS, null, refereeToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.GET, PLAYLISTS, null, refereeToken, List.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.POST, SCREENS, Map.of("host", "127.0.0.1", "port", open.port()), refereeToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, SCREENS + "/control/next", Map.of("screenIds", List.of(1)), refereeToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theBoardPagesOfThisAppAreOffered() {
        ResponseEntity<Map> r = send(HttpMethod.GET, SCREENS + "/board-pages", null, directorToken);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map>) r.getBody().get("pages")).extracting(p -> p.get("path"))
                .contains("/boards/results", "/boards/now-next", "/boards/live-race", "/boards/top");
    }

    // ---- helpers

    private Map<String, Object> playlistBody(String name) {
        return Map.of("name", name, "defaultDuration", 30, "items", List.of(
                Map.of("url", "http://10.0.0.2:8080/boards/live-race", "duration", 60, "label", "Live"),
                Map.of("url", "http://10.0.0.2:8080/boards/results", "duration", 20, "label", "Results")));
    }

    private Long createPlaylist(String name) {
        ResponseEntity<Map> r = send(HttpMethod.POST, PLAYLISTS, playlistBody(name), directorToken);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    @Test
    void aScreenFoundOnTheNetworkWaitsUntilAnOfficialAddsIt() {
        control.discovered("127.0.0.1", open.port());

        assertThat(screens()).noneMatch(s -> s.get("screenId").equals("it-open-" + suffix));
        assertThat(found()).anySatisfy(f -> {
            assertThat(f.get("screenId")).isEqualTo("it-open-" + suffix);
            assertThat(((Number) f.get("port")).intValue()).isEqualTo(open.port());
        });

        add(open, null);

        assertThat(screens()).anyMatch(s -> s.get("screenId").equals("it-open-" + suffix));
        assertThat(found()).noneMatch(f -> f.get("screenId").equals("it-open-" + suffix));
    }

    @Test
    void aScreenAlreadyAddedAndAnsweringIsNotMovedByAnotherDeviceUsingItsId() {
        Long id = ((Number) add(locked, "secret").get("id")).longValue();
        monitor.poll(screenById(id));
        try (FakeScreen impostor = new FakeScreen("it-locked-" + suffix, null)) {
            control.discovered("127.0.0.1", impostor.port());

            assertThat(screenById(id).getPort()).isEqualTo(locked.port());
            assertThat(impostor.calls()).noneMatch(c -> c.contains("(auth)"));
        }
    }

    private List<Map> screens() {
        return (List<Map>) send(HttpMethod.GET, SCREENS, null, directorToken).getBody().get("screens");
    }

    private List<Map> found() {
        return (List<Map>) send(HttpMethod.GET, SCREENS, null, directorToken).getBody().get("found");
    }

    private Map add(FakeScreen screen, String token) {
        Map<String, Object> body = new HashMap<>(Map.of("host", "127.0.0.1", "port", screen.port()));
        if (token != null) {
            body.put("token", token);
        }
        ResponseEntity<Map> r = send(HttpMethod.POST, SCREENS, body, directorToken);
        assertThat(r.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        return r.getBody();
    }

    private Map screenInList(Long id) {
        ResponseEntity<Map> r = send(HttpMethod.GET, SCREENS, null, directorToken);
        return ((List<Map>) r.getBody().get("screens")).stream()
                .filter(s -> ((Number) s.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    private Screen screenById(Long id) {
        return screenRepository.getOrThrow(id);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> send(HttpMethod method, String url, Object body, String token) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private <T> ResponseEntity<T> send(HttpMethod method, String url, Object body, String token, Class<T> type) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers(token)), type);
    }

    private String login(Role role) {
        String email = role.name().toLowerCase() + "-screens-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName(role.name());
        user.setLastName("Screens");
        user.setRoles(Set.of(role));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        ResponseEntity<AuthResponse> r = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().accessToken();
    }
}
