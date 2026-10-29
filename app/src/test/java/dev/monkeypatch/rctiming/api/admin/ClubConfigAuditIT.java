package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Club details, governing bodies, announcer settings, the decoder address and the live-feed switch name who changed them (#139). */
class ClubConfigAuditIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubProfileRepository clubProfileRepository;
    @Autowired ClubProfileService clubProfileService;

    private String run;
    private long adminId;
    private String token;
    private long eventId;
    private String originalHost;
    private Integer originalPort;
    private String originalProtocol;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        token = loginAsAdmin();
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-11-14', 'OPEN') returning id""", Long.class, "Config audit " + run);
        // The club profile is shared by every test: note the decoder address to put it back
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        originalHost = profile.getDecoderHost();
        originalPort = profile.getDecoderPort();
        originalProtocol = profile.getDecoderProtocol();
    }

    @AfterEach
    void tearDown() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        profile.setDecoderHost(originalHost);
        profile.setDecoderPort(originalPort);
        profile.setDecoderProtocol(originalProtocol);
        clubProfileRepository.save(profile);
        jdbc.update("delete from audit_log where actor_user_id = ?", adminId);
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        jdbc.update("delete from governing_body_affiliations where code like ?", "AUD" + run + "%");
        jdbc.update("delete from events where id = ?", eventId);
        jdbc.update("delete from refresh_tokens where user_id = ?", adminId);
        jdbc.update("delete from user_roles where user_id = ?", adminId);
        jdbc.update("delete from users where id = ?", adminId);
    }

    @Test
    void clubDetailsAreRecordedWithBeforeAndAfter() {
        send("PUT", "/api/v1/admin/club/profile", Map.of("name", "Audit Club A " + run, "timezone", "Europe/London"));
        send("PUT", "/api/v1/admin/club/profile", Map.of("name", "Audit Club B " + run, "timezone", "Europe/Paris"));

        List<Map<String, Object>> rows = mine("CLUB_PROFILE_UPDATED");
        assertThat(rows).isNotEmpty();
        Map<String, Object> last = rows.get(rows.size() - 1);
        assertThat(last.get("before_json").toString()).contains("Audit Club A " + run).contains("Europe/London");
        assertThat(last.get("after_json").toString()).contains("Audit Club B " + run).contains("Europe/Paris");
    }

    @Test
    void governingBodiesAreRecordedWhenAddedChangedAndRemoved() {
        ResponseEntity<Map> created = send("POST", "/api/v1/admin/club/affiliations",
                Map.of("code", "AUD" + run, "displayName", "Audit body " + run, "membershipRequired", false));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object id = created.getBody().get("id");
        send("PUT", "/api/v1/admin/club/affiliations/" + id,
                Map.of("code", "AUD" + run, "displayName", "Audit body renamed " + run, "membershipRequired", true));
        assertThat(send("DELETE", "/api/v1/admin/club/affiliations/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from audit_log where entity_type = 'affiliation' and entity_id = ? order by id", String.valueOf(id));
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "AFFILIATION_CREATED", "AFFILIATION_UPDATED", "AFFILIATION_DELETED");
        assertThat(rows.get(1).get("before_json").toString()).contains("\"membershipRequired\":false");
        assertThat(rows.get(1).get("after_json").toString()).contains("\"membershipRequired\":true");
        assertThat(rows.get(2).get("before_json").toString()).contains("Audit body renamed " + run);
        jdbc.update("delete from audit_log where entity_type = 'affiliation' and entity_id = ?", String.valueOf(id));
    }

    @Test
    void announcerSettingsFromBothEndpointsAreRecorded() {
        long before = mine("AUDIO_SETTINGS_CHANGED").size();
        send("PUT", "/api/v1/admin/audio/settings", Map.of(
                "announceCountdown", true, "announceStagger", true, "announceLapBeep", true, "announceFinish", true,
                "announceRunningOrder", true, "runningOrderPercent", 40, "defaultVoiceId", "voice-" + run));
        send("PATCH", "/api/v1/race-control/settings/audio", Map.of("announceLapBeep", false));

        List<Map<String, Object>> rows = mine("AUDIO_SETTINGS_CHANGED");
        assertThat(rows).hasSize((int) before + 2);
        Map<String, Object> admin = rows.get(rows.size() - 2);
        Map<String, Object> patch = rows.get(rows.size() - 1);
        assertThat(admin.get("after_json").toString()).contains("voice-" + run).contains("\"runningOrderPercent\":40");
        assertThat(patch.get("before_json").toString()).contains("\"announceLapBeep\":true");
        assertThat(patch.get("after_json").toString()).contains("\"announceLapBeep\":false");
    }

    @Test
    void aDecoderAddressChangeIsRecordedWithTheOldAndNewAddress() {
        clubProfileService.updateDecoderConfig(Actor.official(adminId), "decoder-" + run, 5100, "RC4");
        clubProfileService.updateDecoderConfig(Actor.official(adminId), "decoder-b-" + run, 5403, "P3");

        List<Map<String, Object>> rows = mine("DECODER_CONFIG_CHANGED");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get("summary").toString()).contains("decoder-b-" + run).contains("5403");
        assertThat(rows.get(1).get("before_json").toString()).contains("decoder-" + run).contains("5100");
        assertThat(rows.get(1).get("after_json").toString()).contains("\"protocol\":\"P3\"");
    }

    @Test
    void theLiveFeedSwitchIsRecordedOnlyWhenItChanges() {
        String url = "/api/v1/race-control/events/" + eventId + "/live-feed";
        send("PUT", url, Map.of("enabled", true));
        send("PUT", url, Map.of("enabled", true));
        send("PUT", url, Map.of("enabled", false));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from audit_log where event_id = ? and action = 'LIVE_FEED_SWITCHED' order by id", eventId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("summary").toString()).contains("on").contains("Config audit " + run);
        assertThat(rows.get(1).get("before_json")).isEqualTo("true");
        assertThat(rows.get(1).get("after_json")).isEqualTo("false");
        assertThat(((Number) rows.get(0).get("actor_user_id")).longValue()).isEqualTo(adminId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> mine(String action) {
        return jdbc.queryForList("select * from audit_log where actor_user_id = ? and action = ? order by id",
                adminId, action);
    }

    private ResponseEntity<Map> send(String method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), Map.class);
    }

    private String loginAsAdmin() {
        User user = new User();
        user.setEmail("config-audit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Config");
        user.setLastName("Admin");
        user.setRoles(Set.of(Role.ADMIN));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        adminId = user.getId();
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
