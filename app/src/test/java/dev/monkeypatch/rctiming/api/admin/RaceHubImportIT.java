package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.media.DriverMediaService;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RaceHub Entry Export v1 import (L7, #15), driven by the fixtures in {@code racehub/}.
 */
class RaceHubImportIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntryRepository entryRepository;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransponderSwapService swapService;
    @Autowired DriverMediaService driverMedia;

    private String adminToken;
    private long adminUserId;
    private String run;
    private long eventId;
    private long buggyClassId;
    private long truckClassId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        adminToken = loginAs(Set.of(Role.ADMIN));

        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, date('now', '+14 days'), 'OPEN') returning id""",
                Long.class, "RaceHub import " + run);
        buggyClassId = createEventClass("RH Buggy " + run);
        truckClassId = createEventClass("RH Truck " + run);
    }

    @Test
    void newImport_createsCompetitorsAndEntries() {
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-initial.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = resp.getBody();
        assertThat(body.get("applied").asBoolean()).isTrue();
        assertThat(body.get("racehubEventName").asString()).isEqualTo("Club Round 3");
        assertThat(body.get("revision").asLong()).isEqualTo(4);
        assertSummary(body, 2, 0, 0, 0, 0, 1);

        Entry ada = entry("a1");
        assertThat(ada.getEventId()).isEqualTo(eventId);
        assertThat(ada.getEventClassId()).isEqualTo(buggyClassId);
        assertThat(ada.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("71" + run);
        assertThat(ada.getSecondaryTransponderNumber()).isEqualTo("72" + run);
        assertThat(ada.getExternalEntryVersion()).isEqualTo(1L);
        assertThat(ada.getRacehubArrival()).isEqualTo("NOT_ARRIVED");

        Competitor adaDriver = competitorRepository.findById(ada.getCompetitorId()).orElseThrow();
        assertThat(adaDriver.getExternalSource()).isEqualTo("RACEHUB");
        assertThat(adaDriver.getExternalId()).isEqualTo("drv-ada-" + run);
        assertThat(adaDriver.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(adaDriver.getBrcaNumber()).isEqualTo("BR" + run);
        assertThat(adaDriver.getHomeClub()).isEqualTo("Analytical RC");

        // Class matched by rc_class_name, ignoring case
        Entry grace = entry("b2");
        assertThat(grace.getEventClassId()).isEqualTo(truckClassId);
        assertThat(grace.getRacehubArrival()).isEqualTo("ARRIVED");

        // Withdrawn and never imported: nothing to create
        assertThat(findEntry("c3")).isNull();

        // The event records when it was imported and from which revision (L8)
        var event = jdbc.queryForMap("select racehub_last_import_at, racehub_last_revision from events where id = ?", eventId);
        assertThat(((Number) event.get("racehub_last_revision")).longValue()).isEqualTo(4);
        assertThat(event.get("racehub_last_import_at")).isNotNull();
    }

    @Test
    void pictureLinksAreReportedAndCarColoursSaved_butNothingIsFetched() {
        JsonNode preview = importFixture("entries-v1-media.json", true).getBody();
        assertThat(preview.get("photoLinks")).isEmpty();
        // Entry 2 has an http address and a colour that is not #rrggbb: warned about, never blocking
        assertThat(preview.get("blocked").asBoolean()).isFalse();
        assertThat(preview.get("warnings").toString()).contains("entry b2-" + run).contains("headshot address");

        JsonNode body = importFixture("entries-v1-media.json", false).getBody();

        assertThat(body.get("applied").asBoolean()).isTrue();
        JsonNode links = body.get("photoLinks");
        assertThat(links.size()).isEqualTo(2);
        Entry ada = entry("a1");
        assertThat(links.get(0).get("kind").asString()).isEqualTo("HEADSHOT");
        assertThat(links.get(0).get("competitorId").asLong()).isEqualTo(ada.getCompetitorId());
        assertThat(links.get(0).get("url").asString()).isEqualTo("https://cdn.example.org/drivers/ada.png");
        assertThat(links.get(1).get("kind").asString()).isEqualTo("CAR_PHOTO");
        assertThat(links.get(1).get("entryId").asLong()).isEqualTo(ada.getId());
        assertThat(driverMedia.carColours(ada.getId())).containsExactly("#ff0000", "#ffffff");
        assertThat(driverMedia.carColours(entry("b2").getId())).isEmpty();
    }

    @Test
    void dryRun_returnsPreviewAndSavesNothing() {
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-initial.json", true);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("dryRun").asBoolean()).isTrue();
        assertThat(resp.getBody().get("applied").asBoolean()).isFalse();
        assertSummary(resp.getBody(), 2, 0, 0, 0, 0, 1);
        assertThat(findEntry("a1")).isNull();
        assertThat(competitorRepository.findByExternalSourceAndExternalId("RACEHUB", "drv-ada-" + run)).isEmpty();
        assertThat(jdbc.queryForObject("select racehub_last_revision from events where id = ?", Long.class, eventId))
                .isNull();
    }

    @Test
    void replay_isANoOp() {
        importFixture("entries-v1-initial.json", false);
        Instant updatedAt = entry("a1").getUpdatedAt();

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-initial.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSummary(resp.getBody(), 0, 0, 0, 2, 0, 1);
        assertThat(entry("a1").getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(entryRepository.findByEventId(eventId)).hasSize(2);
    }

    @Test
    void higherVersion_updatesTheEntry() {
        importFixture("entries-v1-initial.json", false);
        long adaId = entry("a1").getId();

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-update.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSummary(resp.getBody(), 0, 1, 1, 0, 0, 1);
        Entry ada = entry("a1");
        assertThat(ada.getId()).isEqualTo(adaId);
        assertThat(ada.getExternalEntryVersion()).isEqualTo(2L);
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("75" + run);
        assertThat(ada.getSecondaryTransponderNumber()).isNull();
        assertThat(ada.getRacehubArrival()).isEqualTo("ARRIVED");
        assertThat(competitorRepository.findById(ada.getCompetitorId()).orElseThrow().getHomeClub())
                .isEqualTo("Difference Engine RC");
    }

    @Test
    void reImport_leavesASpokenNameAlone() {
        importFixture("entries-v1-initial.json", false);
        Competitor ada = competitorRepository.findById(entry("a1").getCompetitorId()).orElseThrow();
        ada.setSpokenName("Ay-da Luv-lace");
        competitorRepository.save(ada);

        importFixture("entries-v1-update.json", false);

        Competitor after = competitorRepository.findById(ada.getId()).orElseThrow();
        assertThat(after.getHomeClub()).isEqualTo("Difference Engine RC");
        assertThat(after.getSpokenName()).isEqualTo("Ay-da Luv-lace");
    }

    @Test
    void reImport_keepsATransponderSwappedOnTheDay_andFlagsTheDifference() {
        importFixture("entries-v1-initial.json", false);
        Entry ada = entry("a1");
        swapService.swap(eventId, ada.getId(), TransponderSlot.PRIMARY, "99" + run, adminUserId);

        // The update file moves Ada to 75…, but the desk gave her 99… on the day
        JsonNode preview = importFixture("entries-v1-update.json", true).getBody();
        assertThat(preview.get("blocked").asBoolean()).isFalse();
        assertThat(preview.get("warnings").toString())
                .contains("Ada Lovelace keeps transponder 99" + run + " swapped on the day; the file has 75" + run);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-update.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("applied").asBoolean()).isTrue();
        ada = entry("a1");
        assertThat(ada.getExternalEntryVersion()).isEqualTo(2L);
        assertThat(ada.getRacehubArrival()).isEqualTo("ARRIVED");
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("99" + run);
        assertThat(ada.getImportedTransponderNumber()).isEqualTo("75" + run);
        // The secondary wasn't swapped, so the file still sets it
        assertThat(ada.getSecondaryTransponderNumber()).isNull();
        assertThat(ada.getImportedSecondaryTransponderNumber()).isNull();

        JsonNode listed = restTemplate.exchange("/api/v1/admin/entries/events/" + eventId + "/classes/" + buggyClassId,
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), JsonNode.class).getBody();
        JsonNode adaRow = null;
        for (JsonNode row : listed) {
            if (row.get("id").asLong() == ada.getId()) adaRow = row;
        }
        assertThat(adaRow).isNotNull();
        assertThat(adaRow.get("importedTransponderNumber").asString()).isEqualTo("75" + run);

        // Swapping to the file's number settles the difference
        swapService.swap(eventId, ada.getId(), TransponderSlot.PRIMARY, "75" + run, adminUserId);
        assertThat(entry("a1").getImportedTransponderNumber()).isNull();
    }

    @Test
    void reImport_keepsASwappedSecondary_andTheFileStillSetsThePrimary() {
        importFixture("entries-v1-initial.json", false);
        long adaId = entry("a1").getId();
        swapService.swap(eventId, adaId, TransponderSlot.SECONDARY, "98" + run, adminUserId);

        // The update file moves Ada to 75… and gives her secondary 76…, but the desk gave her secondary 98…
        String update = fixture("entries-v1-update.json").replace(
                "\"primary_transponder\": \"75" + run + "\",\n      \"secondary_transponder\": null",
                "\"primary_transponder\": \"75" + run + "\",\n      \"secondary_transponder\": \"76" + run + "\"");
        assertThat(update).contains("\"76" + run + "\"");
        JsonNode body = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=false",
                HttpMethod.POST, new HttpEntity<>(update, adminHeaders()), JsonNode.class).getBody();

        assertThat(body.get("applied").asBoolean()).isTrue();
        assertThat(body.get("warnings").toString())
                .contains("Ada Lovelace keeps secondary transponder 98" + run + " swapped on the day; the file has 76"
                        + run);
        Entry ada = entry("a1");
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("75" + run);
        assertThat(ada.getImportedTransponderNumber()).isNull();
        assertThat(ada.getSecondaryTransponderNumber()).isEqualTo("98" + run);
        assertThat(ada.getImportedSecondaryTransponderNumber()).isEqualTo("76" + run);

        // Swapping the secondary to the file's number settles the difference
        swapService.swap(eventId, adaId, TransponderSlot.SECONDARY, "76" + run, adminUserId);
        assertThat(entry("a1").getImportedSecondaryTransponderNumber()).isNull();
    }

    @Test
    void reImport_withTheSwappedNumber_flagsNothing() {
        importFixture("entries-v1-initial.json", false);
        swapService.swap(eventId, entry("a1").getId(), TransponderSlot.PRIMARY, "75" + run, adminUserId);

        JsonNode body = importFixture("entries-v1-update.json", false).getBody();

        assertThat(body.get("warnings").toString()).doesNotContain("swapped on the day");
        Entry ada = entry("a1");
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("75" + run);
        assertThat(ada.getImportedTransponderNumber()).isNull();
    }

    @Test
    void lowerVersion_isIgnored() {
        importFixture("entries-v1-initial.json", false);
        importFixture("entries-v1-update.json", false);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-stale.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSummary(resp.getBody(), 0, 0, 0, 0, 1, 0);
        Entry ada = entry("a1");
        assertThat(ada.getExternalEntryVersion()).isEqualTo(2L);
        assertThat(ada.getTransponderNumberSnapshot()).isEqualTo("75" + run);
        assertThat(competitorRepository.findById(ada.getCompetitorId()).orElseThrow().getDisplayName())
                .isEqualTo("Ada Lovelace");
    }

    @Test
    void withdrawal_marksWithdrawnAndKeepsRaceHistory() {
        importFixture("entries-v1-initial.json", false);
        long graceId = entry("b2").getId();
        // Grace has already raced: a race entry links her to a heat
        long roundId = jdbc.queryForObject("""
                insert into rounds (event_id, type, round_number, sequence_in_event)
                values (?, 'QUALIFIER', 1, 1) returning id""", Long.class, eventId);
        long raceId = jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, start_type, status)
                values (?, ?, 1, 1, 'STAGGER', 'FINISHED') returning id""", Long.class, roundId, truckClassId);
        jdbc.update("insert into race_entries (race_id, entry_id, grid_position) values (?, ?, 1)", raceId, graceId);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-update.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Entry grace = entryRepository.findById(graceId).orElseThrow();
        assertThat(grace.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
        assertThat(grace.getWithdrawnAt()).isNotNull();
        assertThat(grace.getExternalEntryVersion()).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select count(*) from race_entries where entry_id = ?", Integer.class, graceId))
                .isEqualTo(1);
    }

    @Test
    void unmappedClass_blocksTheImportAndIsListed() {
        ResponseEntity<JsonNode> preview = importFixture("entries-v1-unmapped-class.json", true);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody().get("blocked").asBoolean()).isTrue();
        JsonNode unmapped = preview.getBody().get("unmappedClasses");
        assertThat(unmapped).hasSize(1);
        assertThat(unmapped.get(0).get("racehubEventClassId").asString()).isEqualTo("rh-class-electric-" + run);
        assertThat(unmapped.get(0).get("rcClassName").asString()).isEqualTo("RH Electric Touring " + run);
        assertThat(unmapped.get(0).get("entryCount").asInt()).isEqualTo(1);

        ResponseEntity<JsonNode> blocked = importFixture("entries-v1-unmapped-class.json", false);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(blocked.getBody().get("applied").asBoolean()).isFalse();
        assertThat(findEntry("u1")).isNull();
    }

    @Test
    void classMapping_resolvesAnUnmappedClass() {
        var put = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                        "racehubEventClassId", "rh-class-electric-" + run, "eventClassId", buggyClassId)), adminHeaders()),
                JsonNode.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);

        var get = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), JsonNode.class);
        assertThat(get.getBody()).hasSize(1);
        assertThat(get.getBody().get(0).get("eventClassId").asLong()).isEqualTo(buggyClassId);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-unmapped-class.json", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entry("u1").getEventClassId()).isEqualTo(buggyClassId);
    }

    @Test
    void anAppliedImportIsRecordedButAPreviewABlockedImportAndAReplayAreNot() {
        importFixture("entries-v1-initial.json", true);
        importFixture("entries-v1-unmapped-class.json", false);
        assertThat(auditRows("ENTRIES_IMPORTED")).isEmpty();

        importFixture("entries-v1-initial.json", false);
        importFixture("entries-v1-initial.json", false);
        importFixture("entries-v1-update.json", false);

        List<Map<String, Object>> rows = auditRows("ENTRIES_IMPORTED");
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0).get("actor_user_id")).longValue()).isEqualTo(adminUserId);
        assertThat(rows.get(0).get("summary").toString()).contains("2 new").contains("0 withdrawn");
        assertThat(rows.get(0).get("after_json").toString()).contains("Ada Lovelace").contains("\"revision\":4");
        assertThat(rows.get(1).get("summary").toString()).contains("withdrawn");
    }

    @Test
    void replacingClassMappingsRecordsBeforeAndAfter() {
        String url = "/api/v1/admin/events/" + eventId + "/racehub-class-mappings";
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                "racehubEventClassId", "rh-a-" + run, "eventClassId", buggyClassId)), adminHeaders()), JsonNode.class);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                "racehubEventClassId", "rh-a-" + run, "eventClassId", truckClassId)), adminHeaders()), JsonNode.class);

        List<Map<String, Object>> rows = auditRows("CLASS_MAPPINGS_REPLACED");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("before_json")).isEqualTo("{}");
        assertThat(rows.get(1).get("before_json").toString()).contains("rh-a-" + run).contains(String.valueOf(buggyClassId));
        assertThat(rows.get(1).get("after_json").toString()).contains(String.valueOf(truckClassId));
    }

    @Test
    void classMapping_rejectsAClassFromAnotherEvent() {
        var put = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                        "racehubEventClassId", "rh-class-electric-" + run, "eventClassId", 2001L)), adminHeaders()),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateTransponder_isAWarningNotAnError() {
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-duplicate-transponder.json", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("blocked").asBoolean()).isFalse();
        assertThat(resp.getBody().get("errors")).isEmpty();
        JsonNode warnings = resp.getBody().get("warnings");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).asString())
                .contains("71" + run).contains("Ada Lovelace").contains("Grace Hopper");
        assertThat(entryRepository.findByEventId(eventId)).hasSize(2);
    }

    @Test
    void brcaNumberHeldByAnotherRaceHubCompetitor_blocksTheImportAndNamesWhoHoldsIt() {
        Competitor other = new Competitor();
        other.setDisplayName("Other Holder " + run);
        other.setBrcaNumber("BR" + run);
        other.setExternalSource("RACEHUB");
        other.setExternalId("someone-else-" + run);
        competitorRepository.save(other);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-update.json", true);

        assertThat(resp.getBody().get("blocked").asBoolean()).isTrue();
        assertThat(resp.getBody().get("errors").toString())
                .contains("Ada Lovelace").contains("BRCA number BR" + run)
                .contains("Other Holder " + run + " (already stored)");
    }

    @Test
    void brcaNumberHeldByAWalkIn_isAWarningNotAnError() {
        Competitor walkIn = new Competitor();
        walkIn.setDisplayName("Walk In " + run);
        walkIn.setBrcaNumber("BR" + run);
        competitorRepository.save(walkIn);

        ResponseEntity<JsonNode> resp = importFixture("entries-v1-update.json", true);

        assertThat(resp.getBody().get("blocked").asBoolean()).isFalse();
        assertThat(resp.getBody().get("warnings").toString())
                .contains("Ada Lovelace").contains("Walk In " + run + " (already stored)");
    }

    @Test
    void brcaNumberOfTheSameDriver_isNotBlocked() {
        importFixture("entries-v1-initial.json", false);

        ResponseEntity<JsonNode> again = importFixture("entries-v1-update.json", true);

        assertThat(again.getBody().get("errors").toString()).doesNotContain("BRCA number");
    }

    @Test
    void oneDriverTwiceInAClass_blocksTheImportAndNamesTheClass() {
        // Grace's entry becomes a second entry for Ada in the buggy class
        String json = fixture("entries-v1-duplicate-transponder.json")
                .replace("rh-class-truck-" + run, "rh-class-buggy-" + run)
                .replace("rh truck " + run, "RH Buggy " + run)
                .replace("drv-grace-" + run, "drv-ada-" + run);
        var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=false",
                HttpMethod.POST, new HttpEntity<>(json, adminHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resp.getBody().get("blocked").asBoolean()).isTrue();
        assertThat(resp.getBody().get("errors").get(0).asString())
                .isEqualTo("Ada Lovelace would have 2 entries in RH Buggy " + run);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void nullEntry_blocksTheImportWithAnError() {
        String json = fixture("entries-v1-unmapped-class.json").replace("\"entries\": [", "\"entries\": [null, ");
        var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=true",
                HttpMethod.POST, new HttpEntity<>(json, adminHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("blocked").asBoolean()).isTrue();
        assertThat(resp.getBody().get("errors").get(0).asString()).contains("null");
    }

    @Test
    void wrongSchemaVersion_isRejected() {
        String json = fixture("entries-v1-initial.json").replace("\"schema_version\": 1", "\"schema_version\": 2");
        var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import",
                HttpMethod.POST, new HttpEntity<>(json, adminHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRevision_isRejected() {
        String json = fixture("entries-v1-initial.json").replace("\"revision\": 4,", "");
        var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=true",
                HttpMethod.POST, new HttpEntity<>(json, adminHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdmin_isForbidden() {
        String refereeToken = loginAs(Set.of(Role.REFEREE));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(refereeToken);
        var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=true",
                HttpMethod.POST, new HttpEntity<>(fixture("entries-v1-initial.json"), headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherSource_keepsItsEntriesAndCompetitorsApartFromRaceHubs() {
        assertThat(importFixture("entries-v1-initial.json", false).getStatusCode()).isEqualTo(HttpStatus.OK);

        // The same ids from another booking system are different entries and drivers (#41)
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-initial.json", "OTHER", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSummary(resp.getBody(), 2, 0, 0, 0, 0, 1);
        Entry racehubAda = entry("a1");
        Entry otherAda = entryRepository.findByExternalSourceAndExternalEntryId("OTHER", "a1-" + run).orElseThrow();
        assertThat(otherAda.getId()).isNotEqualTo(racehubAda.getId());
        assertThat(otherAda.getCompetitorId()).isNotEqualTo(racehubAda.getCompetitorId());
        assertThat(otherAda.getEventClassId()).isEqualTo(buggyClassId);
        // Results go back to RaceHub by its class ids, which another system's are not
        assertThat(otherAda.getRacehubEventClassId()).isNull();
        assertThat(racehubAda.getRacehubEventClassId()).isEqualTo("rh-class-buggy-" + run);

        Competitor otherDriver = competitorRepository.findById(otherAda.getCompetitorId()).orElseThrow();
        assertThat(otherDriver.getExternalSource()).isEqualTo("OTHER");
        assertThat(otherDriver.getExternalId()).isEqualTo("drv-ada-" + run);
        assertThat(competitorRepository.findByExternalSourceAndExternalId("RACEHUB", "drv-ada-" + run)).isPresent();
        assertThat(entryRepository.findByEventId(eventId)).hasSize(4);

        // Replaying the other system's file matches its own entries, not RaceHub's
        assertSummary(importFixture("entries-v1-initial.json", "OTHER", true).getBody(), 0, 0, 0, 2, 0, 1);
    }

    @Test
    void anotherSource_doesNotLinkTheEventToRaceHub() {
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-initial.json", "OTHER", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var event = jdbc.queryForMap(
                "select racehub_event_id, racehub_last_revision from events where id = ?", eventId);
        assertThat(event.get("racehub_event_id")).isNull();
        assertThat(event.get("racehub_last_revision")).isNull();

        // So a RaceHub file for any RaceHub event can still be imported alongside it
        assertThat(importFixture("entries-v1-initial.json", false).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherSource_hasItsOwnClassMappings() {
        ResponseEntity<JsonNode> preview = importFixture("entries-v1-unmapped-class.json", "OTHER", true);
        assertThat(preview.getBody().get("unmappedClasses").get(0).get("racehubEventClassId").asString())
                .isEqualTo("OTHER:rh-class-electric-" + run);

        // A RaceHub mapping for the same class id does not apply to the other system's file
        restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                        "racehubEventClassId", "rh-class-electric-" + run, "eventClassId", buggyClassId)), adminHeaders()),
                JsonNode.class);
        assertThat(importFixture("entries-v1-unmapped-class.json", "OTHER", true).getBody().get("blocked").asBoolean())
                .isTrue();

        restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings",
                HttpMethod.PUT, new HttpEntity<>(List.of(Map.of(
                        "racehubEventClassId", "OTHER:rh-class-electric-" + run, "eventClassId", truckClassId)),
                        adminHeaders()),
                JsonNode.class);
        ResponseEntity<JsonNode> resp = importFixture("entries-v1-unmapped-class.json", "OTHER", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entryRepository.findByExternalSourceAndExternalEntryId("OTHER", "u1-" + run).orElseThrow()
                .getEventClassId()).isEqualTo(truckClassId);
    }

    @Test
    void sourceThatCannotBeKeptApart_isRejected() {
        for (String source : List.of("CSV", "other", "")) {
            var resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=true",
                    HttpMethod.POST, new HttpEntity<>(withSource(fixture("entries-v1-initial.json"), source),
                            adminHeaders()), String.class);
            assertThat(resp.getStatusCode()).as(source).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> importFixture(String name, String source, boolean dryRun) {
        return restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=" + dryRun,
                HttpMethod.POST, new HttpEntity<>(withSource(fixture(name), source), adminHeaders()), JsonNode.class);
    }

    private static String withSource(String json, String source) {
        return json.replace("\"schema_version\": 1,", "\"schema_version\": 1, \"source\": \"" + source + "\",");
    }

    private ResponseEntity<JsonNode> importFixture(String name, boolean dryRun) {
        return restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=" + dryRun,
                HttpMethod.POST, new HttpEntity<>(fixture(name), adminHeaders()), JsonNode.class);
    }

    private String fixture(String name) {
        try {
            String raw = new ClassPathResource("racehub/" + name).getContentAsString(StandardCharsets.UTF_8);
            return raw.replace("{{run}}", run);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertSummary(JsonNode body, int created, int updated, int withdrawn,
                                      int unchanged, int stale, int skipped) {
        JsonNode s = body.get("summary");
        assertThat(List.of(s.get("created").asInt(), s.get("updated").asInt(), s.get("withdrawn").asInt(),
                s.get("unchanged").asInt(), s.get("stale").asInt(), s.get("skipped").asInt()))
                .as("created, updated, withdrawn, unchanged, stale, skipped")
                .containsExactly(created, updated, withdrawn, unchanged, stale, skipped);
    }

    private Entry entry(String prefix) {
        Entry e = findEntry(prefix);
        assertThat(e).as("entry " + prefix).isNotNull();
        return e;
    }

    private Entry findEntry(String prefix) {
        return entryRepository.findByExternalSourceAndExternalEntryId("RACEHUB", prefix + "-" + run).orElse(null);
    }

    private long createEventClass(String racingClassName) {
        long racingClassId = jdbc.queryForObject("""
                insert into racing_classes (name) values (?) returning id""",
                Long.class, racingClassName);
        return jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED"}') returning id""",
                Long.class, eventId, racingClassId);
    }

    private List<Map<String, Object>> auditRows(String action) {
        return jdbc.queryForList("select * from audit_log where event_id = ? and action = ? order by id",
                eventId, action);
    }

    private String loginAs(Set<Role> roles) {
        String email = "racehub-" + UUID.randomUUID() + "@test.com";
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        return headers;
    }
}
