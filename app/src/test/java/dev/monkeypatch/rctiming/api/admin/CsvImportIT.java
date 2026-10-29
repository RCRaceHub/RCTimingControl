package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** RC-Timing CSV entry import (#39), driven by the golden files in {@code csvimport/}. */
class CsvImportIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntryRepository entryRepository;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private long adminUserId;
    private String run;
    private long eventId;
    private long buggyClassId;
    private long truckClassId;
    private long fourWheelDriveClassId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        adminToken = loginAsAdmin();
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, date('now', '+14 days'), 'OPEN') returning id""",
                Long.class, "CSV import " + run);
        buggyClassId = createEventClass("CSV Buggy " + run);
        truckClassId = createEventClass("CSV Truck " + run);
        fourWheelDriveClassId = createEventClass("CSV 4WD " + run);
    }

    /** The tests share one database, so each removes what it made, children first. */
    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log where event_id = ?", eventId);
        List<Long> competitorIds = jdbc.queryForList(
                "select distinct competitor_id from entries where event_id = ?", Long.class, eventId);
        jdbc.update("delete from entries where event_id = ?", eventId);
        competitorIds.forEach(id -> jdbc.update(
                "delete from competitors where id = ? and not exists (select 1 from entries where competitor_id = ?)", id, id));
        jdbc.update("delete from competitors where display_name like ? or external_id like ?", "%" + run + "%", "%" + run + "%");
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
    void firstImport_previewsThenCreatesEntries() {
        JsonNode preview = importFile("initial.csv", true).getBody();

        assertThat(preview.get("applied").asBoolean()).isFalse();
        assertSummary(preview, 5, 0, 0, 0, 1);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
        JsonNode ada = row(preview, "NEW", "Ada Lovelace");
        assertThat(ada.get("eventClassId").asLong()).isEqualTo(buggyClassId);
        assertThat(ada.get("info").get("Grade").asText()).isEqualTo("80");
        assertThat(ada.get("info").get("Car Make").asText()).isEqualTo("Associated");
        assertThat(ada.get("info").has("Paid Status")).isFalse();
        assertThat(row(preview, "NEW", "Katherine Johnson " + run).get("info").get("Junior").asText()).isEqualTo("13");

        ResponseEntity<JsonNode> resp = importFile("initial.csv", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("applied").asBoolean()).isTrue();
        assertThat(resp.getBody().get("summary").get("created").asInt()).isEqualTo(5);

        Entry adaEntry = entryFor("Ada Lovelace", buggyClassId);
        assertThat(adaEntry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
        assertThat(adaEntry.getExternalSource()).isEqualTo("CSV");
        assertThat(adaEntry.getTransponderNumberSnapshot()).isEqualTo("71" + run);
        assertThat(adaEntry.getSecondaryTransponderNumber()).isEqualTo("72" + run);
        Competitor adaDriver = competitorRepository.findById(adaEntry.getCompetitorId()).orElseThrow();
        assertThat(adaDriver.getBrcaNumber()).isEqualTo("1" + run);

        // One driver in two classes is one competitor with two entries
        assertThat(entryFor("Alan Turing", buggyClassId).getCompetitorId())
                .isEqualTo(entryFor("Alan Turing", fourWheelDriveClassId).getCompetitorId());
        // BRCA number 0: matched by name, and "PT No 2" of 0 means no second transponder
        Entry grace = entryFor("Grace Hopper " + run, truckClassId);
        assertThat(grace.getSecondaryTransponderNumber()).isNull();
        assertThat(competitorRepository.findById(grace.getCompetitorId()).orElseThrow().getBrcaNumber()).isNull();
    }

    @Test
    void updateRowsAreSkipped_andTheirTranspondersDontWarn() {
        JsonNode body = importFile("initial.csv", false).getBody();

        JsonNode tim = row(body, "SKIPPED", "Tim Berners-Lee");
        assertThat(tim.get("reason").asText()).contains("update");
        assertThat(entries().stream().map(this::competitorName)).doesNotContain("Tim Berners-Lee");
        assertThat(texts(body.get("warnings"))).noneMatch(w -> w.contains("71" + run));
    }

    @Test
    void anAppliedImportIsRecordedButAPreviewAndAReplayAreNot() {
        importFile("initial.csv", true);
        assertThat(auditRows()).isEmpty();

        importFile("initial.csv", false);
        importFile("initial.csv", false);

        List<Map<String, Object>> rows = auditRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("ENTRIES_IMPORTED");
        assertThat(((Number) rows.get(0).get("actor_user_id")).longValue()).isEqualTo(adminUserId);
        assertThat(rows.get(0).get("summary").toString()).contains("5 new").contains("RC-Timing CSV");
        assertThat(rows.get(0).get("after_json").toString()).contains("Ada Lovelace");
    }

    @Test
    void reimportWithNoChanges_isANoOp() {
        importFile("initial.csv", false);
        Instant updatedAt = entryFor("Ada Lovelace", buggyClassId).getUpdatedAt();

        JsonNode body = importFile("initial.csv", false).getBody();

        assertSummary(body, 0, 0, 5, 0, 1);
        assertThat(body.get("summary").get("created").asInt()).isZero();
        assertThat(entryFor("Ada Lovelace", buggyClassId).getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(entries()).hasSize(5);
    }

    @Test
    void aChangedRowIsShownAndAppliedOnlyWhenPicked() {
        importFile("initial.csv", false);

        JsonNode preview = importFile("changed.csv", true).getBody();
        assertSummary(preview, 0, 4, 1, 0, 0);
        JsonNode ada = row(preview, "CHANGED", "Ada Lovelace");
        assertThat(ada.get("changes")).hasSize(1);
        assertThat(ada.get("changes").get(0).get("field").asText()).isEqualTo("Transponder");
        assertThat(ada.get("changes").get(0).get("before").asText()).isEqualTo("71" + run);
        assertThat(ada.get("changes").get(0).get("after").asText()).isEqualTo("77" + run);
        JsonNode alan = row(preview, "CHANGED", "Alan M Turing");
        assertThat(alan.get("changes").get(0).get("before").asText()).isEqualTo("Alan Turing");

        // Nothing picked: nothing changes
        JsonNode nothingPicked = importFile("changed.csv", false).getBody();
        assertThat(nothingPicked.get("applied").asBoolean()).isTrue();
        assertThat(nothingPicked.get("summary").get("updated").asInt()).isZero();
        assertThat(entryFor("Ada Lovelace", buggyClassId).getTransponderNumberSnapshot()).isEqualTo("71" + run);

        // Only Ada picked: only Ada changes
        JsonNode picked = importFile("changed.csv", false, Map.of("update", List.of(ada.get("key").asText()))).getBody();
        assertThat(picked.get("summary").get("updated").asInt()).isEqualTo(1);
        assertThat(entryFor("Ada Lovelace", buggyClassId).getTransponderNumberSnapshot()).isEqualTo("77" + run);
        assertThat(entryFor("Grace Hopper " + run, truckClassId).getSecondaryTransponderNumber()).isNull();
    }

    @Test
    void aMissingEntryIsWithdrawnOnlyWhenPicked_andNeverDeleted() {
        importFile("initial.csv", false);
        long katherineId = entryFor("Katherine Johnson " + run, buggyClassId).getId();

        JsonNode preview = importFile("missing.csv", true).getBody();
        assertSummary(preview, 0, 0, 4, 1, 0);
        JsonNode katherine = row(preview, "MISSING", "Katherine Johnson " + run);
        assertThat(katherine.get("entryId").asLong()).isEqualTo(katherineId);

        importFile("missing.csv", false);
        assertThat(entryRepository.findById(katherineId).orElseThrow().getStatus()).isEqualTo(EntryStatus.CONFIRMED);

        JsonNode withdrawn = importFile("missing.csv", false, Map.of("withdraw", List.of(String.valueOf(katherineId)))).getBody();
        assertThat(withdrawn.get("summary").get("withdrawn").asInt()).isEqualTo(1);
        Entry entry = entryRepository.findById(katherineId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
        assertThat(entry.getWithdrawnAt()).isNotNull();

        // Back in a later file: shown as a change, re-entered when picked
        JsonNode back = importFile("initial.csv", true).getBody();
        JsonNode returning = row(back, "CHANGED", "Katherine Johnson " + run);
        assertThat(returning.get("changes").get(0).get("field").asText()).isEqualTo("Status");
    }

    @Test
    void walkInsAndRaceHubEntriesAreNeverMissing() {
        long walkInDriver = jdbc.queryForObject("""
                insert into competitors (display_name) values (?) returning id""",
                Long.class, "Walk In " + run);
        long raceHubDriver = jdbc.queryForObject("""
                insert into competitors (display_name, external_source, external_id)
                values (?, 'RACEHUB', ?) returning id""", Long.class, "From RaceHub " + run, "drv-" + run);
        jdbc.update("""
                insert into entries (competitor_id, event_id, event_class_id, transponder_number, status)
                values (?, ?, ?, ?, 'CONFIRMED')""", walkInDriver, eventId, truckClassId, "91" + run);
        jdbc.update("""
                insert into entries (competitor_id, event_id, event_class_id, transponder_number, status,
                                     external_source, external_entry_id, external_entry_version)
                values (?, ?, ?, ?, 'CONFIRMED', 'RACEHUB', ?, 1)""",
                raceHubDriver, eventId, truckClassId, "92" + run, "rh-" + run);
        importFile("initial.csv", false);

        JsonNode body = importFile("missing.csv", false, Map.of("withdraw", List.of())).getBody();

        assertThat(rows(body, "MISSING")).extracting(r -> r.get("name").asText())
                .containsExactly("Katherine Johnson " + run);
        assertThat(entries()).filteredOn(e -> e.getStatus() == EntryStatus.CONFIRMED)
                .extracting(this::competitorName).contains("Walk In " + run, "From RaceHub " + run);
    }

    @Test
    void aDuplicateTransponderWarns() {
        ResponseEntity<JsonNode> resp = importFile("duplicate-transponder.csv", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("applied").asBoolean()).isTrue();
        assertThat(texts(resp.getBody().get("warnings")))
                .anyMatch(w -> w.contains("Transponder 71" + run) && w.contains("Ada Lovelace")
                        && w.contains("Grace Hopper " + run));
    }

    @Test
    void anUnmappedClassBlocks_untilItIsMapped() {
        ResponseEntity<JsonNode> resp = importFile("unmapped-class.csv", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resp.getBody().get("blocked").asBoolean()).isTrue();
        JsonNode unmapped = resp.getBody().get("unmappedClasses").get(0);
        String key = "CSV:nitro truggy " + run;
        assertThat(unmapped.get("key").asText()).isEqualTo(key);
        assertThat(unmapped.get("className").asText()).isEqualTo("Nitro Truggy " + run);
        assertThat(entries()).isEmpty();

        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange("/api/v1/admin/events/" + eventId + "/racehub-class-mappings", HttpMethod.PUT,
                new HttpEntity<>(List.of(Map.of("racehubEventClassId", key, "eventClassId", truckClassId)), headers),
                String.class);

        assertThat(importFile("unmapped-class.csv", false).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entryFor("Ada Lovelace", truckClassId).getStatus()).isEqualTo(EntryStatus.CONFIRMED);
    }

    @Test
    void aClassNumberPlacesARowByTheEventsClassOrder() {
        JsonNode body = importFile("class-numbers.csv", false).getBody();

        assertThat(body.get("applied").asBoolean()).isTrue();
        assertThat(entryFor("Ada Lovelace", buggyClassId)).isNotNull();
        assertThat(entryFor("Grace Hopper " + run, truckClassId)).isNotNull();
    }

    @Test
    void aDriverAlreadyInRctc_isReusedAndKeepsTheirName() {
        long raceHubDriver = jdbc.queryForObject("""
                insert into competitors (display_name, brca_number, external_source, external_id)
                values (?, ?, 'RACEHUB', ?) returning id""",
                Long.class, "Ada King " + run, "1" + run, "drv-" + run);

        JsonNode body = importFile("initial.csv", false).getBody();

        assertThat(body.get("applied").asBoolean()).isTrue();
        Entry ada = entries().stream().filter(e -> e.getEventClassId() == buggyClassId
                && e.getCompetitorId() == raceHubDriver).findFirst().orElseThrow();
        assertThat(ada.getExternalSource()).isEqualTo("CSV");
        assertThat(competitorName(ada)).isEqualTo("Ada King " + run);
        assertThat(competitorRepository.findByExternalSourceAndExternalId("CSV", "brca:1" + run)).isEmpty();

        // Read again, the RaceHub name isn't offered as a change
        assertSummary(importFile("initial.csv", true).getBody(), 0, 0, 5, 0, 1);
    }

    @Test
    void aDriverWithNoBrcaNumber_isFoundByNameIgnoringCapitalsAndSpacing() {
        long walkIn = jdbc.queryForObject("""
                insert into competitors (display_name) values (?) returning id""",
                Long.class, "GRACE  HOPPER " + run);

        importFile("initial.csv", false);

        // One entry, on the competitor already there, under the name they already had
        assertThat(entryFor("GRACE  HOPPER " + run, truckClassId).getCompetitorId()).isEqualTo(walkIn);
        assertThat(entries().stream().filter(e -> e.getEventClassId() == truckClassId)).hasSize(1);
    }

    @Test
    void aDriverRaceHubAlreadyEnteredInTheClass_blocks() {
        long raceHubDriver = jdbc.queryForObject("""
                insert into competitors (display_name, brca_number, external_source, external_id)
                values (?, ?, 'RACEHUB', ?) returning id""",
                Long.class, "Ada King " + run, "1" + run, "drv-" + run);
        jdbc.update("""
                insert into entries (competitor_id, event_id, event_class_id, transponder_number, status,
                                     external_source, external_entry_id, external_entry_version)
                values (?, ?, ?, ?, 'CONFIRMED', 'RACEHUB', ?, 1)""",
                raceHubDriver, eventId, buggyClassId, "71" + run, "rh-" + run);

        ResponseEntity<JsonNode> resp = importFile("initial.csv", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(texts(resp.getBody().get("errors"))).anyMatch(e -> e.contains("would have 2 entries in CSV Buggy " + run));
        assertThat(entries()).hasSize(1);
    }

    @Test
    void aClassGivenByNumberThenByName_isTheSameEntry() {
        importFile("class-numbers.csv", false);
        long adaId = entryFor("Ada Lovelace", buggyClassId).getId();

        JsonNode body = importFile("class-names.csv", false).getBody();

        assertSummary(body, 0, 0, 2, 0, 0);
        assertThat(entries()).hasSize(2);
        assertThat(entryFor("Ada Lovelace", buggyClassId).getId()).isEqualTo(adaId);
    }

    @Test
    void namesWithQuotesOrCommasAreRejectedClearly() {
        ResponseEntity<JsonNode> resp = importFile("bad-names.csv", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        List<String> errors = texts(resp.getBody().get("errors"));
        assertThat(errors).anyMatch(e -> e.startsWith("Line 2") && e.contains("double quote"));
        assertThat(errors).anyMatch(e -> e.startsWith("Line 3") && e.contains("has 5 values but the header has 4")
                && e.contains("comma"));
        assertThat(entries()).isEmpty();
    }

    @Test
    void aPickThatNoLongerMatchesTheFileBlocks() {
        importFile("initial.csv", false);

        ResponseEntity<JsonNode> resp = importFile("initial.csv", false, Map.of("update", List.of("not-a-row")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(texts(resp.getBody().get("errors"))).anyMatch(e -> e.contains("Preview the file again"));
    }

    @Test
    void onlyAdminsImport() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/csv-import",
                HttpMethod.POST, new HttpEntity<>(form("initial.csv", Map.of()), headers), String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("select * from audit_log where event_id = ? order by id", eventId);
    }

    private ResponseEntity<JsonNode> importFile(String name, boolean dryRun) {
        return importFile(name, dryRun, Map.of());
    }

    private ResponseEntity<JsonNode> importFile(String name, boolean dryRun, Map<String, List<String>> picks) {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/v1/admin/events/" + eventId + "/csv-import?dryRun=" + dryRun,
                HttpMethod.POST, new HttpEntity<>(form(name, picks), headers), JsonNode.class);
    }

    private MultiValueMap<String, Object> form(String name, Map<String, List<String>> picks) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(fixture(name).getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        picks.forEach((field, values) -> values.forEach(v -> form.add(field, v)));
        return form;
    }

    private String fixture(String name) {
        try {
            return new ClassPathResource("csvimport/" + name).getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{run}}", run);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertSummary(JsonNode body, int newEntries, int changed, int unchanged, int missing, int skipped) {
        JsonNode s = body.get("summary");
        assertThat(List.of(s.get("newEntries").asInt(), s.get("changed").asInt(), s.get("unchanged").asInt(),
                s.get("missing").asInt(), s.get("skipped").asInt()))
                .as("new, changed, unchanged, missing, skipped; errors %s", body.get("errors"))
                .containsExactly(newEntries, changed, unchanged, missing, skipped);
    }

    private static List<JsonNode> rows(JsonNode body, String group) {
        return StreamSupport.stream(body.get("rows").spliterator(), false)
                .filter(r -> group.equals(r.get("group").asText())).toList();
    }

    private static JsonNode row(JsonNode body, String group, String name) {
        return rows(body, group).stream().filter(r -> name.equals(r.get("name").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("No " + group + " row for " + name + " in " + body));
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void anAppliedUpdateLeavesASpokenNameAlone() {
        importFile("initial.csv", false);
        Competitor alan = entries().stream()
                .map(e -> competitorRepository.findById(e.getCompetitorId()).orElseThrow())
                .filter(c -> "Alan Turing".equals(c.getDisplayName()))
                .findFirst().orElseThrow();
        alan.setSpokenName("Al-an Tyoor-ing");
        competitorRepository.save(alan);

        JsonNode preview = importFile("changed.csv", true).getBody();
        String key = row(preview, "CHANGED", "Alan M Turing").get("key").asText();
        importFile("changed.csv", false, Map.of("update", List.of(key)));

        Competitor after = competitorRepository.findById(alan.getId()).orElseThrow();
        assertThat(after.getDisplayName()).isEqualTo("Alan M Turing");
        assertThat(after.getSpokenName()).isEqualTo("Al-an Tyoor-ing");
    }

    private List<Entry> entries() {
        return entryRepository.findByEventId(eventId);
    }

    private String competitorName(Entry e) {
        return competitorRepository.findById(e.getCompetitorId()).map(Competitor::getDisplayName).orElse(null);
    }

    private Entry entryFor(String name, long eventClassId) {
        return entries().stream()
                .filter(e -> e.getEventClassId() == eventClassId && name.equals(competitorName(e)))
                .findFirst().orElseThrow(() -> new AssertionError("No entry for " + name));
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

    private String loginAsAdmin() {
        String email = "csv-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(Set.of(Role.ADMIN));
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
        headers.setBearerAuth(adminToken);
        return headers;
    }
}
