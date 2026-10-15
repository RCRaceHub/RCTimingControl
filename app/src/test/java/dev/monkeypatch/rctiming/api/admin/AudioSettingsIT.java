package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two audio settings endpoints (AUDIO-07): the admin form's PUT keeps the countdown intervals it does not
 * carry, race control's PATCH changes only the fields it is sent, and both refuse an out-of-range depth (#137).
 */
class AudioSettingsIT extends AbstractIntegrationTest {

    private static final String ADMIN_SETTINGS = "/api/v1/admin/audio/settings";
    private static final String RACE_CONTROL_SETTINGS = "/api/v1/race-control/settings/audio";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ClubProfileRepository clubProfileRepository;
    @Autowired ClubProfileService clubProfileService;

    private ClubAudioSettings originalSettings;
    private String originalVoice;
    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        // The club profile is shared by every test: remember it, and put it back afterwards
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        originalSettings = profile.getAudioSettings();
        originalVoice = profile.getDefaultVoiceId();
        profile.setAudioSettings(new ClubAudioSettings(true, true, true, true, true, 3, new int[]{900, 120}));
        clubProfileRepository.save(profile);
        token = loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        profile.setAudioSettings(originalSettings);
        profile.setDefaultVoiceId(originalVoice);
        clubProfileRepository.save(profile);
        userRepository.deleteById(userId);
    }

    @Test
    void theAdminFormKeepsTheCountdownIntervalsItDoesNotCarry() {
        Map<String, Object> form = Map.of(
                "announceCountdown", false, "announceStagger", true, "announceLapBeep", true,
                "announceFinish", true, "announceRunningOrder", true, "runningOrderDepth", 5,
                "defaultVoiceId", "en_GB-alan-medium");

        ResponseEntity<JsonNode> saved = call(HttpMethod.PUT, ADMIN_SETTINGS, form);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClubAudioSettings now = clubProfileRepository
                .findById(clubProfileService.getSingletonProfileId()).orElseThrow().getAudioSettings();
        assertThat(now.announceCountdown()).isFalse();
        assertThat(now.runningOrderDepth()).isEqualTo(5);
        assertThat(now.countdownIntervals()).containsExactly(900, 120);
    }

    @Test
    void theAdminFormKeepsTheHornsAndLeadInItDoesNotCarry() {
        call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("startHorn", false, "startCountdownSeconds", 10));
        Map<String, Object> form = Map.of(
                "announceCountdown", true, "announceStagger", true, "announceLapBeep", true,
                "announceFinish", true, "announceRunningOrder", true, "runningOrderDepth", 3,
                "defaultVoiceId", "en_GB-alan-medium");

        assertThat(call(HttpMethod.PUT, ADMIN_SETTINGS, form).getStatusCode()).isEqualTo(HttpStatus.OK);

        ClubAudioSettings now = clubProfileRepository
                .findById(clubProfileService.getSingletonProfileId()).orElseThrow().getAudioSettings();
        assertThat(now.startHorn()).isFalse();
        assertThat(now.finishHorn()).isTrue();
        assertThat(now.startCountdownSeconds()).isEqualTo(10);
    }

    @Test
    void raceControlCanChangeTheHornsAndLeadIn() {
        ResponseEntity<JsonNode> saved = call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS,
                Map.of("finishHorn", false, "announceNextRace", false, "startCountdownSeconds", 5));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("finishHorn").asBoolean()).isFalse();
        assertThat(saved.getBody().get("announceNextRace").asBoolean()).isFalse();
        assertThat(saved.getBody().get("startHorn").asBoolean()).isTrue();
        assertThat(saved.getBody().get("startCountdownSeconds").asInt()).isEqualTo(5);
    }

    @Test
    void raceControlCanWordTheTimeCallAndSwitchOffTheCarFinishCalls() {
        ResponseEntity<JsonNode> saved = call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS,
                Map.of("timeUpText", "Time up, one more lap.", "announceCarFinish", false));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("timeUpText").asText()).isEqualTo("Time up, one more lap.");
        assertThat(saved.getBody().get("announceCarFinish").asBoolean()).isFalse();

        ResponseEntity<JsonNode> reset = call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("timeUpText", ""));
        assertThat(reset.getBody().get("timeUpText").asText()).isEqualTo(ClubAudioSettings.DEFAULT_TIME_UP_TEXT);
        assertThat(reset.getBody().get("announceCarFinish").asBoolean()).isFalse();
    }

    @Test
    void countdownIntervalsThatWouldFloodTheSpeechEngineAreRefused() {
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("countdownIntervals", new int[]{0}))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("countdownIntervals", new int[]{99999}))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS,
                Map.of("countdownIntervals", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("countdownIntervals", new int[]{300, 60}))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void savingTheAdminFormWithNoVoiceChosen_keepsTheStoredVoice() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        profile.setDefaultVoiceId("");
        clubProfileRepository.save(profile);
        JsonNode shown = call(HttpMethod.GET, ADMIN_SETTINGS, null).getBody();
        assertThat(shown.get("defaultVoiceId").asText()).isEmpty();

        Map<String, Object> form = new HashMap<>(Map.of(
                "announceCountdown", true, "announceStagger", true, "announceLapBeep", true,
                "announceFinish", true, "announceRunningOrder", true, "runningOrderDepth", 3));
        form.put("defaultVoiceId", null);
        ResponseEntity<JsonNode> saved = call(HttpMethod.PUT, ADMIN_SETTINGS, form);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clubProfileRepository.findById(profile.getId()).orElseThrow().getDefaultVoiceId()).isEmpty();
    }

    @Test
    void raceControlChangesOnlyTheFieldsItIsSent() {
        ResponseEntity<JsonNode> patched = call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS,
                Map.of("announceLapBeep", false));

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The answer is the settings as saved: the one change, everything else as it was
        JsonNode saved = patched.getBody();
        assertThat(saved.get("announceLapBeep").asBoolean()).isFalse();
        assertThat(saved.get("announceCountdown").asBoolean()).isTrue();
        assertThat(saved.get("runningOrderDepth").asInt()).isEqualTo(3);
        assertThat(saved.get("countdownIntervals")).hasSize(2);
        ClubAudioSettings now = clubProfileRepository
                .findById(clubProfileService.getSingletonProfileId()).orElseThrow().getAudioSettings();
        assertThat(now.announceLapBeep()).isFalse();
        assertThat(now.announceFinish()).isTrue();
        assertThat(now.countdownIntervals()).containsExactly(900, 120);
    }

    @Test
    void aDepthOutsideOneToTwentyIsRefused() {
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("runningOrderDepth", 0)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("runningOrderDepth", 21)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> form = Map.of(
                "announceCountdown", true, "announceStagger", true, "announceLapBeep", true,
                "announceFinish", true, "announceRunningOrder", true, "runningOrderDepth", 500,
                "defaultVoiceId", "en_GB-alan-medium");
        assertThat(call(HttpMethod.PUT, ADMIN_SETTINGS, form).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ClubAudioSettings now = clubProfileRepository
                .findById(clubProfileService.getSingletonProfileId()).orElseThrow().getAudioSettings();
        assertThat(now.runningOrderDepth()).isEqualTo(3);
    }

    @Test
    void aClubSoundReplacesTheStartHornAndRemovingItGoesBack() {
        ResponseEntity<JsonNode> uploaded = uploadHorn("start", "audio/wav", new byte[]{1, 2, 3}, 1500);

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody().get("startMs").asInt()).isEqualTo(1500);
        assertThat(uploaded.getBody().get("startUrl").asText()).contains("horns/start-").endsWith(".wav");
        assertThat(uploaded.getBody().get("finishUrl").isNull()).isTrue();
        // race control sees it with the rest of the settings, and changing a toggle keeps it
        call(HttpMethod.PATCH, RACE_CONTROL_SETTINGS, Map.of("startHorn", true));
        JsonNode shown = call(HttpMethod.GET, RACE_CONTROL_SETTINGS, null).getBody();
        assertThat(shown.get("hornSounds").get("startMs").asInt()).isEqualTo(1500);

        ResponseEntity<JsonNode> removed = call(HttpMethod.DELETE, "/api/v1/admin/audio/horns/start", null);

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removed.getBody().get("startUrl").isNull()).isTrue();
    }

    @Test
    void aHornSoundThatIsNotShortAudioIsRefused() {
        assertThat(uploadHorn("finish", "text/html", new byte[]{1}, 1000).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadHorn("finish", "audio/mpeg", new byte[]{1}, 60_000).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadHorn("finish", "audio/mpeg", new byte[2 * 1024 * 1024], 1000).getStatusCode())
                .isIn(HttpStatus.BAD_REQUEST, HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(uploadHorn("siren", "audio/mpeg", new byte[]{1}, 1000).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theAdminFormKeepsTheClubHornSounds() {
        uploadHorn("finish", "audio/ogg", new byte[]{9}, 2000);
        Map<String, Object> form = Map.of(
                "announceCountdown", true, "announceStagger", true, "announceLapBeep", true,
                "announceFinish", true, "announceRunningOrder", true, "runningOrderDepth", 3,
                "defaultVoiceId", "en_GB-alan-medium");

        call(HttpMethod.PUT, ADMIN_SETTINGS, form);

        ClubAudioSettings now = clubProfileRepository
                .findById(clubProfileService.getSingletonProfileId()).orElseThrow().getAudioSettings();
        assertThat(now.hornSounds().finishMs()).isEqualTo(2000);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> uploadHorn(String horn, String contentType, byte[] bytes, int durationMs) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "horn"; }
        }, partHeaders));
        body.add("durationMs", String.valueOf(durationMs));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/v1/admin/audio/horns/" + horn, HttpMethod.PUT,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String loginAsAdmin() {
        User user = new User();
        user.setEmail("audio-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Audio");
        user.setLastName("Admin");
        user.setRoles(Set.of(Role.ADMIN));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        userId = user.getId();
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
