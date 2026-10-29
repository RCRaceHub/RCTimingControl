package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedRepository;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedScheduler;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pulling an event's entries from a URL (#42), against a stub booking system: success, an unchanged revision,
 * a refused token, an unreachable feed, and an automatic fetch held for an official.
 */
class EntryFeedIT extends AbstractIntegrationTest {

    private static final String TOKEN = "feed-token-1234abcd";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntryRepository entryRepository;
    @Autowired EntryFeedRepository feedRepository;
    @Autowired EntryFeedScheduler scheduler;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private Long adminUserId;
    private String run;
    private long eventId;
    private long buggyClassId;
    private HttpServer feedServer;
    private volatile String feedBody;
    private volatile int feedStatus = 200;
    /** Run by the stand-in feed while it answers a request, to change things mid-fetch. */
    private volatile Runnable duringFetch;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        adminToken = loginAs(Set.of(Role.ADMIN));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, date('now', '+14 days'), 'OPEN') returning id""",
                Long.class, "Entry feed " + run);
        buggyClassId = createEventClass("RH Buggy " + run);
        createEventClass("RH Truck " + run);

        feedServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        feedServer.createContext("/entries", exchange -> {
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            Runnable midFetch = duringFetch;
            if (midFetch != null) {
                midFetch.run();
            }
            byte[] body = feedBody == null ? new byte[0] : feedBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(feedStatus, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        feedServer.start();
        // Leave automatic fetching to the tests that ask for it
        jdbc.update("update entry_feeds set auto_fetch = 0");
    }

    @AfterEach
    void tearDown() {
        feedServer.stop(0);
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        jdbc.update("delete from entry_feeds where event_id = ?", eventId);
        List<Long> competitorIds = jdbc.queryForList(
                "select distinct competitor_id from entries where event_id = ?", Long.class, eventId);
        jdbc.update("delete from entries where event_id = ?", eventId);
        competitorIds.forEach(id -> jdbc.update(
                "delete from competitors where id = ? and not exists (select 1 from entries where competitor_id = ?)", id, id));
        jdbc.update("delete from racehub_class_mappings where event_id = ?", eventId);
        List<Long> racingClassIds = jdbc.queryForList(
                "select racing_class_id from event_classes where event_id = ?", Long.class, eventId);
        jdbc.update("delete from event_classes where event_id = ?", eventId);
        racingClassIds.forEach(id -> jdbc.update("delete from racing_classes where id = ?", id));
        jdbc.update("delete from events where id = ?", eventId);
        jdbc.update("delete from refresh_tokens where user_id = ?", adminUserId);
        jdbc.update("delete from user_roles where user_id = ?", adminUserId);
        jdbc.update("delete from users where id = ?", adminUserId);
    }

    @Test
    void fetchNow_holdsTheFileUntilAnOfficialConfirmsIt() {
        feedBody = fixture("entries-v1-initial.json");
        JsonNode saved = saveFeed(feedUrl(), TOKEN, false).getBody();
        // The token is never sent back, only its last four characters
        assertThat(saved.toString()).doesNotContain(TOKEN);
        assertThat(saved.get("tokenSaved").asBoolean()).isTrue();
        assertThat(saved.get("tokenHint").asText()).isEqualTo("abcd");
        assertThat(jdbc.queryForObject("select token_encrypted from entry_feeds where event_id = ?", String.class, eventId))
                .doesNotContain(TOKEN);

        JsonNode fetched = post("/fetch").getBody();

        assertThat(authorizations).containsExactly("Bearer " + TOKEN);
        assertThat(fetched.get("lastStatus").asText()).isEqualTo("WAITING");
        assertThat(fetched.get("waiting").asBoolean()).isTrue();
        assertThat(fetched.get("waitingRevision").asLong()).isEqualTo(4);
        assertThat(fetched.get("lastFetchAt").isNull()).isFalse();
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
        // Only what the import reads is kept: the file's email and date of birth are not
        assertThat(jdbc.queryForObject("select held_document from entry_feeds where event_id = ?", String.class, eventId))
                .doesNotContain("ada@example.com").doesNotContain("1815-12-10");

        JsonNode preview = post("/preview").getBody();
        assertThat(preview.get("dryRun").asBoolean()).isTrue();
        assertThat(preview.at("/summary/created").asInt()).isEqualTo(2);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();

        ResponseEntity<JsonNode> applied = post("/apply");
        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applied.getBody().get("applied").asBoolean()).isTrue();
        assertThat(entryRepository.findByEventId(eventId)).hasSize(2);

        JsonNode feed = getFeed().getBody();
        assertThat(feed.get("lastStatus").asText()).isEqualTo("APPLIED");
        assertThat(feed.get("appliedRevision").asLong()).isEqualTo(4);
        assertThat(feed.get("waiting").asBoolean()).isFalse();
    }

    @Test
    void anOversizedAddress_isRefusedByValidationBeforeTheServiceSeesIt() {
        String tooLong = "https://example.test/" + "a".repeat(2000);

        ResponseEntity<JsonNode> response = saveFeed(tooLong, TOKEN, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnchangedRevision_isANoOp() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, true);
        scheduler.fetchAll();
        assertThat(getFeed().getBody().get("lastStatus").asText()).isEqualTo("APPLIED");
        String entriesBefore = entriesSnapshot();

        scheduler.fetchAll();
        JsonNode fetched = post("/fetch").getBody();

        assertThat(authorizations).hasSize(3);
        assertThat(fetched.get("lastStatus").asText()).isEqualTo("UNCHANGED");
        assertThat(fetched.get("waiting").asBoolean()).isFalse();
        assertThat(entriesSnapshot()).isEqualTo(entriesBefore);
    }

    @Test
    void automaticFetch_appliesANewRevisionThatImportsCleanly() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, true);
        scheduler.fetchAll();
        assertThat(entryRepository.findByEventId(eventId)).hasSize(2);

        feedBody = fixture("entries-v1-update.json");
        scheduler.fetchAll();

        JsonNode feed = getFeed().getBody();
        assertThat(feed.get("lastStatus").asText()).isEqualTo("APPLIED");
        assertThat(feed.get("appliedRevision").asLong()).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select status from entries where external_source = 'RACEHUB' and external_entry_id = ?",
                String.class, "b2-" + run)).isEqualTo("WITHDRAWN");
    }

    @Test
    void automaticFetch_holdsABlockedImportForAnOfficial() {
        feedBody = fixture("entries-v1-unmapped-class.json");
        saveFeed(feedUrl(), TOKEN, true);

        scheduler.fetchAll();

        JsonNode feed = getFeed().getBody();
        assertThat(feed.get("lastStatus").asText()).isEqualTo("WAITING");
        assertThat(feed.get("waiting").asBoolean()).isTrue();
        assertThat(feed.get("lastMessage").asText()).contains("1 class needs mapping");
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
        assertThat(post("/apply").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // The official maps the class and confirms
        restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                        "racehubEventClassId", "rh-class-electric-" + run, "eventClassId", buggyClassId)), headers()),
                JsonNode.class);
        assertThat(post("/preview").getBody().get("blocked").asBoolean()).isFalse();
        assertThat(post("/apply").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entryRepository.findByEventId(eventId)).hasSize(1);
        assertThat(getFeed().getBody().get("lastStatus").asText()).isEqualTo("APPLIED");
    }

    @Test
    void aRefusedToken_isShownNotThrown() {
        feedStatus = 401;
        saveFeed(feedUrl(), TOKEN, false);

        ResponseEntity<JsonNode> fetched = post("/fetch");

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("lastStatus").asText()).isEqualTo("AUTH_FAILED");
        assertThat(fetched.getBody().get("lastMessage").asText()).contains("token");
        assertThat(fetched.getBody().get("waiting").asBoolean()).isFalse();
    }

    @Test
    void changingTheUrl_clearsTheOldAddressesOutcome() {
        feedStatus = 401;
        saveFeed(feedUrl(), TOKEN, false);
        post("/fetch");

        JsonNode moved = saveFeed(feedUrl() + "?event=2", TOKEN, false).getBody();

        assertThat(moved.get("lastStatus").isNull()).isTrue();
        assertThat(moved.get("lastMessage").isNull()).isTrue();
        assertThat(moved.get("lastFetchAt").isNull()).isTrue();
    }

    @Test
    void anUnreachableFeed_isShownNotThrown() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        saveFeed("http://127.0.0.1:" + closedPort + "/entries", TOKEN, true);

        ResponseEntity<JsonNode> fetched = post("/fetch");
        scheduler.fetchAll();

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("lastStatus").asText()).isEqualTo("FAILED");
        assertThat(getFeed().getBody().get("lastStatus").asText()).isEqualTo("FAILED");
        assertThat(getFeed().getBody().get("lastMessage").asText()).contains("Couldn't reach the feed");
    }

    @Test
    void aFileThatIsNotAnEntryExport_isShownNotThrown() {
        feedBody = "<html>Sign in</html>";
        saveFeed(feedUrl(), TOKEN, false);

        JsonNode fetched = post("/fetch").getBody();

        assertThat(fetched.get("lastStatus").asText()).isEqualTo("FAILED");
        assertThat(fetched.get("lastMessage").asText()).contains("entry file");
    }

    @Test
    void savingWithoutAToken_keepsTheSavedOne() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, false);

        JsonNode saved = saveFeed(feedUrl(), null, true).getBody();
        post("/fetch");

        assertThat(saved.get("tokenSaved").asBoolean()).isTrue();
        assertThat(saved.get("autoFetch").asBoolean()).isTrue();
        assertThat(authorizations).containsExactly("Bearer " + TOKEN);
    }

    @Test
    void plainHttpToAnotherComputer_isRefused() {
        ResponseEntity<JsonNode> resp = saveFeed("http://booking.example.com/entries", TOKEN, false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aFeedRemovedWhileItIsFetched_staysRemovedAndFetchNowIsA404() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, false);
        duringFetch = () -> jdbc.update("delete from entry_feeds where event_id = ?", eventId);

        ResponseEntity<JsonNode> fetched = post("/fetch");

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select count(*) from entry_feeds where event_id = ?", Integer.class, eventId))
                .isZero();
    }

    @Test
    void aFeedMovedToAnotherAddressWhileItIsFetched_keepsNoOutcomeFromTheOldOne() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, false);
        String otherAddress = feedUrl() + "?moved";
        // An official points the feed somewhere else while the first fetch is still in flight
        duringFetch = () -> jdbc.update("update entry_feeds set url = ? where event_id = ?", otherAddress, eventId);

        JsonNode fetched = post("/fetch").getBody();

        // The file came from the old address, so nothing is held or recorded against the new one
        assertThat(fetched.get("waiting").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select url from entry_feeds where event_id = ?", String.class, eventId))
                .isEqualTo(otherAddress);
        assertThat(jdbc.queryForObject("select held_document from entry_feeds where event_id = ?", String.class, eventId))
                .isNull();
        assertThat(jdbc.queryForObject("select last_status from entry_feeds where event_id = ?", String.class, eventId))
                .isNull();
    }

    @Test
    void anEventWithoutAFeed_hasNoContent() {
        assertThat(getFeed().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void nonAdmin_isForbidden() {
        String refereeToken = loginAs(Set.of(Role.REFEREE));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refereeToken);
        var resp = restTemplate.exchange(feedPath(""), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void settingsFetchAndApplyAreRecordedWithoutTheToken() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, false);
        saveFeed(feedUrl(), null, true);
        post("/fetch");
        assertThat(post("/apply").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(feedPath(""), HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from audit_log where event_id = ? order by id", eventId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "ENTRY_FEED_CREATED", "ENTRY_FEED_UPDATED", "ENTRIES_IMPORTED", "ENTRY_FEED_DELETED");
        assertThat(rows).allSatisfy(r ->
                assertThat(((Number) r.get("actor_user_id")).longValue()).isEqualTo(adminUserId));
        assertThat(rows.get(0).get("before_json")).isNull();
        assertThat(rows.get(0).get("after_json").toString()).contains("\"tokenSaved\":true").contains("\"autoFetch\":false");
        assertThat(rows.get(1).get("before_json").toString()).contains("\"autoFetch\":false");
        assertThat(rows.get(1).get("after_json").toString()).contains("\"autoFetch\":true").contains("\"tokenChanged\":false");
        assertThat(rows.get(2).get("summary").toString()).contains("2 new");
        assertThat(rows.get(3).get("before_json").toString()).contains(feedUrl());
        // The token is never written to the log
        assertThat(rows.toString()).doesNotContain(TOKEN);
    }

    @Test
    void anAutomaticImportIsRecordedAsTheSystem() {
        feedBody = fixture("entries-v1-initial.json");
        saveFeed(feedUrl(), TOKEN, true);
        scheduler.fetchAll();

        Map<String, Object> row = jdbc.queryForList(
                "select * from audit_log where event_id = ? and action = 'ENTRIES_IMPORTED'", eventId).get(0);

        assertThat(row.get("actor_user_id")).isNull();
        assertThat(row.get("source").toString()).isEqualToIgnoringCase("system");
        assertThat(row.get("actor_label").toString()).contains("entry-feed");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> saveFeed(String url, String token, boolean autoFetch) {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("token", token);
        body.put("autoFetch", autoFetch);
        return restTemplate.exchange(feedPath(""), HttpMethod.PUT, new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getFeed() {
        return restTemplate.exchange(feedPath(""), HttpMethod.GET, new HttpEntity<>(headers()), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String action) {
        return restTemplate.exchange(feedPath(action), HttpMethod.POST, new HttpEntity<>(headers()), JsonNode.class);
    }

    private String feedPath(String action) {
        return "/api/v1/admin/events/" + eventId + "/entry-feed" + action;
    }

    private String feedUrl() {
        return "http://127.0.0.1:" + feedServer.getAddress().getPort() + "/entries";
    }

    private String entriesSnapshot() {
        return jdbc.queryForList("select id, status, external_entry_version, updated_at from entries where event_id = ? "
                + "order by id", eventId).toString();
    }

    private String fixture(String name) {
        try {
            return new ClassPathResource("racehub/" + name).getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{run}}", run);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private long createEventClass(String racingClassName) {
        long racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, racingClassName);
        return jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED"}') returning id""",
                Long.class, eventId, racingClassId);
    }

    private String loginAs(Set<Role> roles) {
        String email = "feed-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        adminUserId = userRepository.save(user).getId();
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        return headers;
    }
}
