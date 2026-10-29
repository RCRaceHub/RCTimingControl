package dev.monkeypatch.rctiming.api.admin;

import tools.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How a competitor's name is said aloud: set, cleared and heard (#119). */
class AdminCompetitorControllerIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CompetitorService competitorService;
    @Autowired CompetitorRepository competitorRepository;

    @MockitoBean PiperTtsClient piperClient;

    @Test
    void anAdminSetsAndClearsASpokenName_andTheListShowsIt() {
        String token = loginAs(Set.of(Role.ADMIN));
        Competitor c = competitorService.createWalkIn("Siobhan Keane " + UUID.randomUUID());

        ResponseEntity<JsonNode> set = put(token, c.getId(), "  Shiv-awn Keen ");
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(set.getBody().get("spokenName").asString()).isEqualTo("Shiv-awn Keen");
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getSpokenName()).isEqualTo("Shiv-awn Keen");

        JsonNode listed = list(token, c.getId());
        assertThat(listed.get("spokenName").asString()).isEqualTo("Shiv-awn Keen");
        assertThat(listed.get("displayName").asString()).isEqualTo(c.getDisplayName());
        assertThat(listed.get("speechName").asString()).isEqualTo("Shiv-awn Keen");

        ResponseEntity<JsonNode> history = restTemplate.exchange("/api/v1/admin/competitors/" + c.getId() + "/changes",
                HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(1);
        assertThat(history.getBody().get(0).get("by").asString()).isEqualTo("Staff User");
        assertThat(history.getBody().get(0).get("action").asString()).isEqualTo("SPOKEN_NAME_CHANGED");
        assertThat(history.getBody().get(0).get("before").isNull()).isTrue();
        assertThat(history.getBody().get(0).get("after").asString()).isEqualTo("Shiv-awn Keen");

        ResponseEntity<JsonNode> cleared = put(token, c.getId(), "");
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("spokenName").isNull()).isTrue();
        // With none set, the display name is what is spoken (a name with nothing to tidy is unchanged)
        assertThat(cleared.getBody().get("speechName").asString()).isEqualTo(c.getDisplayName());
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getSpokenName()).isNull();
    }

    @Test
    void aSpokenNameOverTheLimitIsRefused() {
        String token = loginAs(Set.of(Role.ADMIN));
        Competitor c = competitorService.createWalkIn("Long Name " + UUID.randomUUID());

        ResponseEntity<JsonNode> resp = put(token, c.getId(), "x".repeat(101));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownCompetitorIsNotFound() {
        String token = loginAs(Set.of(Role.ADMIN));

        assertThat(put(token, 999_999_999L, "x").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRaceDirectorOrRefereeCanChangeASpokenNameAndItIsRecordedAgainstThem() {
        Competitor c = competitorService.createWalkIn("Desk Fix " + UUID.randomUUID());
        String director = loginAs(Set.of(Role.RACE_DIRECTOR));
        String referee = loginAs(Set.of(Role.REFEREE));

        assertThat(put(director, c.getId(), "Desk Fix-ed").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getSpokenName()).isEqualTo("Desk Fix-ed");
        assertThat(put(referee, c.getId(), "Desk Fix-ed Again").getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode history = restTemplate.exchange("/api/v1/admin/competitors/" + c.getId() + "/changes",
                HttpMethod.GET, new HttpEntity<>(headers(loginAs(Set.of(Role.ADMIN)))), JsonNode.class).getBody();
        assertThat(history).hasSize(2);
        // Newest first
        assertThat(history.get(0).get("before").asString()).isEqualTo("Desk Fix-ed");
        assertThat(history.get(0).get("after").asString()).isEqualTo("Desk Fix-ed Again");
        assertThat(history.get(1).get("after").asString()).isEqualTo("Desk Fix-ed");
    }

    @Test
    void savingTheSameSpokenNameAgainIsNotRecordedAsAChange() {
        String admin = loginAs(Set.of(Role.ADMIN));
        Competitor c = competitorService.createWalkIn("Same Twice " + UUID.randomUUID());

        put(admin, c.getId(), "Same-ee");
        put(admin, c.getId(), "  Same-ee ");

        JsonNode history = restTemplate.exchange("/api/v1/admin/competitors/" + c.getId() + "/changes",
                HttpMethod.GET, new HttpEntity<>(headers(admin)), JsonNode.class).getBody();
        assertThat(history).hasSize(1);
    }

    @Test
    void onlyAnAdminCanExportOrAnonymiseACompetitor() {
        Competitor c = competitorService.createWalkIn("Private Person " + UUID.randomUUID());
        String director = loginAs(Set.of(Role.RACE_DIRECTOR));
        String admin = loginAs(Set.of(Role.ADMIN));
        String url = "/api/v1/admin/competitors/" + c.getId();

        assertThat(restTemplate.exchange(url + "/personal-data", HttpMethod.GET,
                new HttpEntity<>(headers(director)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url + "/anonymise", HttpMethod.POST,
                new HttpEntity<>(headers(director)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getDisplayName()).isEqualTo(c.getDisplayName());

        ResponseEntity<JsonNode> export = restTemplate.exchange(url + "/personal-data", HttpMethod.GET,
                new HttpEntity<>(headers(admin)), JsonNode.class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("competitor-" + c.getId());
        assertThat(export.getBody().get("competitor").get("display_name").asString()).isEqualTo(c.getDisplayName());

        assertThat(restTemplate.exchange(url + "/anonymise", HttpMethod.POST,
                new HttpEntity<>(headers(admin)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Anonymised competitor #" + c.getId());
    }

    @Test
    void onlyAnAdminSeesTheChangeHistory() {
        Competitor c = competitorService.createWalkIn("History Private " + UUID.randomUUID());

        ResponseEntity<JsonNode> resp = restTemplate.exchange("/api/v1/admin/competitors/" + c.getId() + "/changes",
                HttpMethod.GET, new HttpEntity<>(headers(loginAs(Set.of(Role.REFEREE)))), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousCallersCannotChangeASpokenName() {
        Competitor c = competitorService.createWalkIn("Anonymous " + UUID.randomUUID());

        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                "/api/v1/admin/competitors/" + c.getId() + "/spoken-name", HttpMethod.PUT,
                new HttpEntity<>(Map.of("spokenName", "x")), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
        assertThat(competitorRepository.findById(c.getId()).orElseThrow().getSpokenName()).isNull();
    }

    @Test
    void previewReturnsTheSpokenAudio() {
        String token = loginAs(Set.of(Role.ADMIN));
        byte[] wav = {0x52, 0x49, 0x46, 0x46};
        when(piperClient.synthesize(eq("Shiv-awn Keen"), any())).thenReturn(wav);

        ResponseEntity<byte[]> resp = restTemplate.exchange("/api/v1/admin/competitors/spoken-name/preview",
                HttpMethod.POST, new HttpEntity<>(Map.of("text", " Shiv-awn Keen "), headers(token)), byte[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("audio/wav"));
        assertThat(resp.getBody()).isEqualTo(wav);
        verify(piperClient).synthesize(eq("Shiv-awn Keen"), any());
    }

    @Test
    void previewIs503WhenThePiperVoiceIsDown() {
        String token = loginAs(Set.of(Role.ADMIN));
        when(piperClient.synthesize(anyString(), any())).thenThrow(new TtsUnavailableException("down"));

        ResponseEntity<JsonNode> resp = restTemplate.exchange("/api/v1/admin/competitors/spoken-name/preview",
                HttpMethod.POST, new HttpEntity<>(Map.of("text", "x"), headers(token)), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mergePreviewAndMergeWorkForAnAdminAndAreRefusedForOthers() {
        String admin = loginAs(Set.of(Role.ADMIN));
        Competitor keep = competitorService.createWalkIn("Merge Keep " + UUID.randomUUID());
        Competitor duplicate = competitorService.createWalkIn("Merge Dup " + UUID.randomUUID());
        put(admin, duplicate.getId(), "Dup-ee");

        ResponseEntity<JsonNode> preview = restTemplate.exchange(
                "/api/v1/admin/competitors/merge-preview?keepId=" + keep.getId() + "&duplicateId=" + duplicate.getId(),
                HttpMethod.GET, new HttpEntity<>(headers(admin)), JsonNode.class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody().get("canMerge").asBoolean()).isTrue();
        assertThat(preview.getBody().get("resultingSpokenName").asString()).isEqualTo("Dup-ee");

        ResponseEntity<JsonNode> refused = restTemplate.exchange("/api/v1/admin/competitors/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("keepId", keep.getId(), "duplicateId", duplicate.getId()),
                        headers(loginAs(Set.of(Role.RACE_DIRECTOR, Role.REFEREE)))), JsonNode.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(competitorRepository.findById(duplicate.getId())).isPresent();

        ResponseEntity<JsonNode> merged = restTemplate.exchange("/api/v1/admin/competitors/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("keepId", keep.getId(), "duplicateId", duplicate.getId()), headers(admin)),
                JsonNode.class);
        assertThat(merged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merged.getBody().get("keptCompetitorId").asLong()).isEqualTo(keep.getId());
        assertThat(competitorRepository.findById(duplicate.getId())).isEmpty();
        assertThat(competitorRepository.findById(keep.getId()).orElseThrow().getSpokenName()).isEqualTo("Dup-ee");
    }

    @Test
    void mergingACompetitorWithItselfIsABadRequestAndAnUnknownOneIsNotFound() {
        String admin = loginAs(Set.of(Role.ADMIN));
        Competitor c = competitorService.createWalkIn("Merge Self " + UUID.randomUUID());

        assertThat(restTemplate.exchange("/api/v1/admin/competitors/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("keepId", c.getId(), "duplicateId", c.getId()), headers(admin)),
                JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange("/api/v1/admin/competitors/merge", HttpMethod.POST,
                new HttpEntity<>(Map.of("keepId", c.getId(), "duplicateId", 999_999_999L), headers(admin)),
                JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void possibleDuplicatesListsCompetitorsThatShareANameOrABrcaNumber() {
        String admin = loginAs(Set.of(Role.ADMIN));
        String tag = UUID.randomUUID().toString();
        Competitor a = competitorService.createWalkIn("Twin Name " + tag);
        Competitor b = competitorService.createWalkIn("TWIN  name " + tag);
        competitorService.createWalkIn("Not A Twin " + tag);

        ResponseEntity<JsonNode> resp = restTemplate.exchange("/api/v1/admin/competitors/possible-duplicates",
                HttpMethod.GET, new HttpEntity<>(headers(admin)), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode group = null;
        for (JsonNode g : resp.getBody()) {
            for (JsonNode c : g.get("competitors")) {
                if (c.get("id").asLong() == a.getId()) group = g;
            }
        }
        assertThat(group).isNotNull();
        assertThat(group.get("reason").asString()).isEqualTo("Same name");
        assertThat(group.get("competitors")).hasSize(2);
        assertThat(group.get("competitors").findValuesAsString("displayName")).contains(b.getDisplayName());
    }

    private ResponseEntity<JsonNode> put(String token, long id, String spokenName) {
        return restTemplate.exchange("/api/v1/admin/competitors/" + id + "/spoken-name", HttpMethod.PUT,
                new HttpEntity<>(Map.of("spokenName", spokenName), headers(token)), JsonNode.class);
    }

    private JsonNode list(String token, long id) {
        JsonNode all = restTemplate.exchange("/api/v1/admin/competitors", HttpMethod.GET,
                new HttpEntity<>(headers(token)), JsonNode.class).getBody();
        for (JsonNode n : all) {
            if (n.get("id").asLong() == id) return n;
        }
        throw new AssertionError("competitor " + id + " not listed");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String loginAs(Set<Role> roles) {
        String email = "spoken-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }
}
