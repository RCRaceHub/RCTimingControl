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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Audio templates: the starters, saving the settings now, applying one, the unique name, deleting and who may. */
class AudioTemplateIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/race-control/settings/audio/templates";
    private static final String SETTINGS = "/api/v1/race-control/settings/audio";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ClubProfileRepository clubProfileRepository;
    @Autowired ClubProfileService clubProfileService;
    @Autowired JdbcTemplate jdbc;

    private ClubAudioSettings originalSettings;
    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        originalSettings = profile.getAudioSettings();
        token = login(Role.RACE_DIRECTOR);
    }

    @AfterEach
    void tearDown() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        profile.setAudioSettings(originalSettings);
        clubProfileRepository.save(profile);
        jdbc.update("delete from audio_templates where name like 'IT %'");
        userRepository.deleteById(userId);
    }

    private Long idOf(String name) {
        JsonNode list = call(HttpMethod.GET, URL, null, token).getBody();
        return StreamSupport.stream(list.spliterator(), false)
                .filter(t -> t.get("name").asText().equals(name)).findFirst().orElseThrow().get("id").asLong();
    }

    @Test
    void theStarterTemplatesAreThere() {
        JsonNode list = call(HttpMethod.GET, URL, null, token).getBody();
        assertThat(StreamSupport.stream(list.spliterator(), false).map(t -> t.get("name").asText()))
                .contains("Club night", "BRCA meeting", "Practice");
    }

    @Test
    void applyingAStarterChangesTheSwitchesButKeepsTheHornFiles() {
        ClubProfile profile = clubProfileRepository.findById(clubProfileService.getSingletonProfileId()).orElseThrow();
        ClubAudioSettings withHorn = profile.getAudioSettings().withHornSounds(
                new ClubAudioSettings.HornSounds("/own-start.wav", 1200, null, null));
        profile.setAudioSettings(withHorn);
        clubProfileRepository.save(profile);

        ResponseEntity<JsonNode> applied = call(HttpMethod.POST, URL + "/" + idOf("Practice") + "/apply", null, token);

        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applied.getBody().get("startHorn").asBoolean()).isFalse();
        assertThat(applied.getBody().get("startCountdownSeconds").asInt()).isZero();
        assertThat(applied.getBody().get("hornSounds").get("startUrl").asText()).isEqualTo("/own-start.wav");
    }

    @Test
    void savingTheSettingsNowAndApplyingThemLater() {
        call(HttpMethod.PATCH, SETTINGS, Map.of("announceFinish", false, "paceBeeps", false), token);
        ResponseEntity<JsonNode> created = call(HttpMethod.POST, URL, Map.of("name", "IT saved"), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        call(HttpMethod.PATCH, SETTINGS, Map.of("announceFinish", true, "paceBeeps", true), token);

        ResponseEntity<JsonNode> applied = call(HttpMethod.POST,
                URL + "/" + created.getBody().get("id").asLong() + "/apply", null, token);

        assertThat(applied.getBody().get("announceFinish").asBoolean()).isFalse();
        assertThat(applied.getBody().get("paceBeeps").asBoolean()).isFalse();
    }

    @Test
    void aNameCannotBeUsedTwiceOrBeBlank() {
        assertThat(call(HttpMethod.POST, URL, Map.of("name", "IT dup"), token).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(call(HttpMethod.POST, URL, Map.of("name", "it DUP"), token).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, URL, Map.of("name", "  "), token).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTemplateCanBeUpdatedAndDeleted() {
        long id = call(HttpMethod.POST, URL, Map.of("name", "IT temp"), token).getBody().get("id").asLong();
        call(HttpMethod.PATCH, SETTINGS, Map.of("finishHorn", false), token);

        ResponseEntity<JsonNode> updated = call(HttpMethod.PUT, URL + "/" + id, Map.of(), token);
        assertThat(updated.getBody().get("settings").get("finishHorn").asBoolean()).isFalse();

        assertThat(call(HttpMethod.DELETE, URL + "/" + id, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.DELETE, URL + "/" + id, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRefereeCannotUseTemplates() {
        String referee = login(Role.REFEREE);
        assertThat(call(HttpMethod.GET, URL, null, referee).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, URL + "/" + idOf("Practice") + "/apply", null, referee).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String login(Role role) {
        User user = new User();
        user.setEmail("tmpl-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName("Audio");
        user.setLastName("Template");
        user.setRoles(Set.of(role));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        if (role == Role.RACE_DIRECTOR) {
            userId = user.getId();
        }
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(user.getEmail(), "password123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
