package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.TestHttp;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rotating the RaceHub key from the admin page: the request succeeds, the entry feeds that point at RaceHub get the
 * new key and the change is audited without it. The store is shared with every other integration test, so each test
 * leaves no saved key behind.
 */
class RaceHubKeyIT extends AbstractIntegrationTest {

    private static final String KEY_PATH = "/api/v1/admin/racehub/connection/key";

    @Autowired TestHttp restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired RaceHubKeyStore keyStore;
    @Autowired ClubProfileService clubProfileService;
    @Autowired DatabaseProperties database;

    private HttpHeaders headers;
    private long feedId;

    @BeforeEach
    void setUp() {
        headers = adminHeaders();
        clubProfileService.getSingletonProfileId();
        jdbc.update("update club_profiles set racehub_url = 'https://racehub.example'");
        long eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'DRAFT') returning id""", Long.class, "Key rotation " + UUID.randomUUID());
        var saved = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/entry-feed", HttpMethod.PUT,
                new HttpEntity<>(Map.of("url", "https://racehub.example/api/rctc/v1/events/1/entries",
                        "token", "old-club-key-1111", "autoFetch", false), headers), String.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        feedId = jdbc.queryForObject("select id from entry_feeds where event_id = ?", Long.class, eventId);
    }

    @AfterEach
    void tidyUp() {
        keyStore.clear();
        jdbc.update("delete from entry_feeds where id = ?", feedId);
        jdbc.update("update club_profiles set racehub_url = null");
    }

    @Test
    void savingAKeySucceedsUpdatesTheRaceHubFeedsAndIsAudited() {
        long before = lastAuditId();

        ResponseEntity<JsonNode> response = restTemplate.exchange(KEY_PATH, HttpMethod.PUT,
                new HttpEntity<>(Map.of("key", "new-club-key-2222"), headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keyStore.saved()).contains("new-club-key-2222");
        assertThat(tokenHint()).isEqualTo("2222");
        assertThat(auditSince(before, "RACEHUB_KEY_CHANGED"))
                .contains("\"keySource\":\"SAVED\"", "\"feedsChanged\":")
                .doesNotContain("new-club-key");
    }

    @Test
    void aKeyThatCannotBeWrittenLeavesTheFeedsAndTheAuditLogAsTheyWere() throws IOException {
        long before = lastAuditId();
        // A folder where the temporary key file goes makes writing it fail
        Path blocker = database.effectiveDataDirectory().resolve(RaceHubKeyStore.FILE_NAME + ".tmp");
        Files.createDirectory(blocker);
        try {
            ResponseEntity<String> response = restTemplate.exchange(KEY_PATH, HttpMethod.PUT,
                    new HttpEntity<>(Map.of("key", "new-club-key-2222"), headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            Files.delete(blocker);
        }
        assertThat(keyStore.saved()).isEmpty();
        assertThat(tokenHint()).isEqualTo("1111");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where id > ? and action = ?", Integer.class,
                before, "RACEHUB_KEY_CHANGED")).isZero();
    }

    @Test
    void clearingTheKeySucceedsUpdatesTheRaceHubFeedsAndIsAudited() {
        restTemplate.exchange(KEY_PATH, HttpMethod.PUT,
                new HttpEntity<>(Map.of("key", "new-club-key-2222"), headers), JsonNode.class);
        long before = lastAuditId();

        ResponseEntity<JsonNode> response = restTemplate.exchange(KEY_PATH, HttpMethod.DELETE,
                new HttpEntity<>(headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keyStore.saved()).isEmpty();
        // The test settings have no settings-file key, so the feed is left with none
        assertThat(tokenHint()).isNull();
        assertThat(auditSince(before, "RACEHUB_KEY_CLEARED")).contains("\"keySource\":\"NONE\"");
    }

    private String tokenHint() {
        return jdbc.queryForObject("select token_hint from entry_feeds where id = ?", String.class, feedId);
    }

    private long lastAuditId() {
        return jdbc.queryForObject("select coalesce(max(id), 0) from audit_log", Long.class);
    }

    private String auditSince(long id, String action) {
        return jdbc.queryForObject("select after_json from audit_log where id > ? and action = ?",
                String.class, id, action);
    }

    private HttpHeaders adminHeaders() {
        String email = "racehub-key-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"),
                AuthResponse.class);
        HttpHeaders result = new HttpHeaders();
        result.setContentType(MediaType.APPLICATION_JSON);
        result.setBearerAuth(login.getBody().accessToken());
        return result;
    }
}
