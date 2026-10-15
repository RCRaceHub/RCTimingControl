package dev.monkeypatch.rctiming.resultsexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Results Export v1 (#27): exports are queued when a race finishes, when a finished race is corrected and at
 * day close; they queue while RaceHub is unreachable and go when it returns; and sending one twice is safe.
 */
class ResultsExportIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ResultsOutboxRepository outboxRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ResultsExportService exportService;
    @Autowired ClubProfileService clubProfileService;
    @Autowired RestClient.Builder restClientBuilder;

    private String token;
    private String run;
    private long eventId;
    private long buggyClassId;
    private long roundId;
    private HttpServer raceHub;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        token = loginAs(Set.of(Role.ADMIN, Role.RACE_DIRECTOR, Role.REFEREE));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, '2026-10-18', 'IN_PROGRESS') returning id""",
                Long.class, "Results export " + run);
        buggyClassId = createEventClass("RH Buggy " + run);
        createEventClass("RH Truck " + run);
        roundId = jdbc.queryForObject("""
                insert into rounds (event_id, type, round_number, sequence_in_event)
                values (?, 'FINAL', 1, 1) returning id""", Long.class, eventId);
    }

    @AfterEach
    void stopRaceHub() {
        if (raceHub != null) {
            raceHub.stop(0);
        }
    }

    @Test
    void eventsWithoutARaceHubImportAreNotQueued() {
        assertThat(exportService.enqueue(eventId, ExportReason.DAY_CLOSE)).isEmpty();
        assertThat(outbox()).isEmpty();
    }

    @Test
    void importRecordsTheRaceHubEventAndEachEntrysClass() {
        importEntries();

        assertThat(jdbc.queryForObject("select racehub_event_id from events where id = ?", String.class, eventId))
                .isEqualTo("evt-" + run);
        assertThat(jdbc.queryForObject("select racehub_event_class_id from entries where external_entry_id = ?",
                String.class, "a1-" + run)).isEqualTo("rh-class-buggy-" + run);
    }

    @Test
    void anAbandonedRaceIsExportedWhenItFinishes() {
        importEntries();
        long raceId = race("RUNNING");

        ResponseEntity<Void> abandon = restTemplate.exchange("/api/v1/race-control/race/" + raceId + "/abandon",
                HttpMethod.POST, new HttpEntity<>(headers()), Void.class);
        assertThat(abandon.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResultsOutboxItem item = awaitOutbox(1).get(0);
        assertThat(item.getReason()).isEqualTo(ExportReason.RACE_FINISHED);
        assertThat(item.getRevision()).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(OutboxStatus.QUEUED);
        JsonNode export = json(item.getPayload());
        assertThat(export.at("/event/racehub_event_id").asText()).isEqualTo("evt-" + run);
        assertThat(export.at("/races/0/rctc_race_id").asLong()).isEqualTo(raceId);
        assertThat(export.at("/races/0/status").asText()).isEqualTo("ABANDONED");
    }

    @Test
    void aFinishedRaceExportCarriesRaceHubIdsWalkInsAndPenalties() throws IOException {
        importEntries();
        long ada = entryId("a1-" + run);
        long walkIn = walkIn("Walk-in Wendy");
        long raceId = finishedRace(ada, walkIn);
        long finishedAt = jdbc.queryForObject("select finished_at from races where id = ?", Long.class, raceId);
        penalty(raceId, walkIn, "TIME", "5.0", "Jump start", finishedAt - 60_000_000L);
        penalty(raceId, ada, "LAP", "1", "Short cut", finishedAt - 60_000_000L);
        penalty(raceId, ada, "LAP", "1", "Missed marshalling", finishedAt + 60_000_000L);

        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();

        JsonNode export = json(item.getPayload());
        assertThat(ResultsExportV1GoldenTest.validate(export)).isEmpty();
        JsonNode race = export.at("/races/0");
        assertThat(race.at("/racehub_event_class_ids/0").asText()).isEqualTo("rh-class-buggy-" + run);
        assertThat(race.at("/status").asText()).isEqualTo("FINISHED");
        JsonNode first = race.at("/results/0");
        assertThat(first.at("/external_source").asText()).isEqualTo("RACEHUB");
        assertThat(first.at("/entry_id").asText()).isEqualTo("a1-" + run);
        assertThat(first.at("/driver_profile_id").asText()).isEqualTo("drv-ada-" + run);
        assertThat(first.at("/laps").asInt()).isEqualTo(12);
        // Every penalty since the race started counts in the stored result (#63)
        assertThat(first.at("/penalties/0/reason").asText()).isEqualTo("Short cut");
        assertThat(first.at("/penalties/0/included_in_result").asBoolean()).isTrue();
        assertThat(first.at("/penalties/1/reason").asText()).isEqualTo("Missed marshalling");
        assertThat(first.at("/penalties/1/included_in_result").asBoolean()).isTrue();
        JsonNode second = race.at("/results/1");
        assertThat(second.at("/external_source").isNull()).isTrue();
        assertThat(second.at("/entry_id").isNull()).isTrue();
        assertThat(second.at("/display_name").asText()).isEqualTo("Walk-in Wendy");
        assertThat(second.at("/penalties/0/type").asText()).isEqualTo("TIME");
        assertThat(second.at("/penalties/0/value").decimalValue()).isEqualByComparingTo("5");
        assertThat(second.at("/penalties/0/included_in_result").asBoolean()).isTrue();
        // Nothing personal goes out: the import file carried an email and date of birth
        assertThat(item.getPayload()).doesNotContain("ada@example.com").doesNotContain("1815-12-10");
    }

    @Test
    void theExportNamesTheEntrysCurrentCompetitorNotTheOneStoredInTheSnapshot() throws IOException {
        // A merge moves an entry to another competitor, and leaves the stored snapshot as it was
        importEntries();
        long walkIn = walkIn("Walk-in Wendy");
        long raceId = finishedRace(entryId("a1-" + run), walkIn);
        jdbc.update("update result_snapshots set positions_json = replace(positions_json, "
                + "'\"entryId\":" + walkIn + ",\"competitorId\":null', '\"entryId\":" + walkIn
                + ",\"competitorId\":987654321') where race_id = ?", raceId);
        long currentCompetitor = jdbc.queryForObject("select competitor_id from entries where id = ?", Long.class, walkIn);

        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();

        JsonNode second = json(item.getPayload()).at("/races/0/results/1");
        assertThat(second.at("/rctc_entry_id").asLong()).isEqualTo(walkIn);
        assertThat(second.at("/rctc_competitor_id").asLong()).isEqualTo(currentCompetitor);
    }

    @Test
    void anEntryFromAnotherSourceIsSentLikeAWalkIn() throws IOException {
        importEntries();
        long ada = entryId("a1-" + run);
        // From a CSV import or another booking system (#41): RaceHub doesn't know its ids
        long competitorId = jdbc.queryForObject("""
                insert into competitors (display_name, external_source, external_id)
                values ('Other Olive', 'OTHER', ?) returning id""", Long.class, "drv-olive-" + run);
        long other = jdbc.queryForObject("""
                insert into entries (event_id, event_class_id, competitor_id, transponder_number, status,
                                     external_source, external_entry_id)
                values (?, ?, ?, ?, 'CONFIRMED', 'OTHER', ?) returning id""",
                Long.class, eventId, buggyClassId, competitorId, "8" + run, "o1-" + run);
        finishedRace(ada, other);

        JsonNode export = json(exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow().getPayload());

        assertThat(ResultsExportV1GoldenTest.validate(export)).isEmpty();
        JsonNode second = export.at("/races/0/results/1");
        assertThat(second.at("/rctc_entry_id").asLong()).isEqualTo(other);
        assertThat(second.at("/external_source").isNull()).isTrue();
        assertThat(second.at("/entry_id").isNull()).isTrue();
        assertThat(second.at("/driver_profile_id").isNull()).isTrue();
    }

    @Test
    void aCorrectionToAFinishedRaceSendsAHigherRevision() {
        importEntries();
        long ada = entryId("a1-" + run);
        long raceId = finishedRace(ada, walkIn("Walk-in Wendy"));
        ResultsOutboxItem first = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();

        ResponseEntity<String> penalty = restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/penalty",
                HttpMethod.POST, new HttpEntity<>(Map.of("entryId", ada, "penaltyType", "LAP", "value", 1,
                        "reason", "Cut the track"), headers()), String.class);
        assertThat(penalty.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<ResultsOutboxItem> items = awaitOutbox(2);
        ResultsOutboxItem correction = items.get(1);
        assertThat(correction.getReason()).isEqualTo(ExportReason.CORRECTION);
        assertThat(correction.getRevision()).isGreaterThan(first.getRevision());
        assertThat(json(correction.getPayload()).at("/races/0/results/0/penalties/0/reason").asText())
                .isEqualTo("Cut the track");
        // Only the newest export needs to go
        assertThat(items.get(0).getStatus()).isEqualTo(OutboxStatus.SUPERSEDED);
    }

    @Test
    void aMarshalAdjustmentToAFinishedRaceIsACorrection() {
        importEntries();
        long ada = entryId("a1-" + run);
        long raceId = finishedRace(ada, walkIn("Walk-in Wendy"));

        ResponseEntity<String> adjust = restTemplate.exchange("/api/v1/race-control/race/" + raceId + "/marshal-adjustment",
                HttpMethod.POST, new HttpEntity<>(Map.of("entryId", ada, "transponderNumber", "1234567", "lapDelta", 1), headers()), String.class);
        assertThat(adjust.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(awaitOutbox(1).get(0).getReason()).isEqualTo(ExportReason.CORRECTION);
    }

    @Test
    void aResultStoredBeforeCorrectionsKeepsItsPenaltiesOutstanding() {
        importEntries();
        long ada = entryId("a1-" + run);
        long raceId = finishedRace(ada, walkIn("Walk-in Wendy"));
        jdbc.update("update result_snapshots set timed_positions_json = null where race_id = ?", raceId);
        long finishedAt = jdbc.queryForObject("select finished_at from races where id = ?", Long.class, raceId);
        penalty(raceId, ada, "TIME", "5.0", "Jump start", finishedAt - 60_000_000L);

        JsonNode first = json(exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow().getPayload())
                .at("/races/0/results/0");
        assertThat(first.at("/penalties/0/type").asText()).isEqualTo("TIME");
        assertThat(first.at("/penalties/0/included_in_result").asBoolean()).isFalse();
    }

    @Test
    void aLapPenaltyMustBeAWholeNumberOfLaps() {
        importEntries();
        long ada = entryId("a1-" + run);
        long raceId = finishedRace(ada, walkIn("Walk-in Wendy"));

        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/penalty",
                HttpMethod.POST, new HttpEntity<>(Map.of("entryId", ada, "penaltyType", "LAP", "value", 1.5,
                        "reason", "Half a lap"), headers()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void correctionsAfterTheFinishChangeTheStoredAndExportedResult() {
        importEntries();
        long ada = entryId("a1-" + run);
        long wendy = walkIn("Walk-in Wendy");
        long raceId = finishedRace(ada, wendy);

        // A lap off Ada puts both on 11 laps, with Ada still quicker; 5 seconds more puts Wendy ahead (#63)
        penalty(raceId, ada, "LAP", 1, "Cut the track");
        penalty(raceId, ada, "TIME", 5, "Jumped the start");

        JsonNode stored = json(jdbc.queryForObject(
                "select positions_json from result_snapshots where race_id = ?", String.class, raceId));
        assertThat(stored.at("/0/entryId").asLong()).isEqualTo(wendy);
        assertThat(stored.at("/1/entryId").asLong()).isEqualTo(ada);
        assertThat(stored.at("/1/lapsCompleted").asInt()).isEqualTo(11);
        assertThat(stored.at("/1/totalTimeMs").asLong()).isEqualTo(305_100L);
        assertThat(stored.at("/1/position").asInt()).isEqualTo(2);
        // The result as timed is kept, so later corrections start from it again
        JsonNode timed = json(jdbc.queryForObject(
                "select timed_positions_json from result_snapshots where race_id = ?", String.class, raceId));
        assertThat(timed.at("/0/entryId").asLong()).isEqualTo(ada);
        assertThat(timed.at("/0/lapsCompleted").asInt()).isEqualTo(12);

        JsonNode exported = json(awaitOutbox(1).get(0).getPayload()).at("/races/0/results");
        assertThat(exported.at("/0/display_name").asText()).isEqualTo("Walk-in Wendy");
        assertThat(exported.at("/1/laps").asInt()).isEqualTo(11);
        assertThat(exported.at("/1/penalties/0/included_in_result").asBoolean()).isTrue();
        assertThat(exported.at("/1/penalties/1/included_in_result").asBoolean()).isTrue();

        // The race director credits Ada a missed lap, which puts her back in front
        ResponseEntity<String> adjust = restTemplate.exchange("/api/v1/race-control/race/" + raceId + "/marshal-adjustment",
                HttpMethod.POST, new HttpEntity<>(Map.of("entryId", ada, "transponderNumber", "1234567", "lapDelta", 1),
                        headers()), String.class);
        assertThat(adjust.getStatusCode()).isEqualTo(HttpStatus.OK);

        stored = json(jdbc.queryForObject(
                "select positions_json from result_snapshots where race_id = ?", String.class, raceId));
        assertThat(stored.at("/0/entryId").asLong()).isEqualTo(ada);
        assertThat(stored.at("/0/lapsCompleted").asInt()).isEqualTo(12);
    }

    @Test
    void closingTheDayQueuesAnExport() {
        importEntries();

        ResponseEntity<String> close = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/transition",
                HttpMethod.POST, new HttpEntity<>(Map.of("targetStatus", "COMPLETED"), headers()), String.class);
        assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResultsOutboxItem item = awaitOutbox(1).get(0);
        assertThat(item.getReason()).isEqualTo(ExportReason.DAY_CLOSE);
        assertThat(json(item.getPayload()).at("/event/status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void exportsQueueWhileRaceHubIsUnreachableAndGoWhenItReturns() throws IOException {
        importEntries();
        finishedRace(entryId("a1-" + run), walkIn("Walk-in Wendy"));
        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();
        int port = freePort();
        ResultsSender sender = sender(port);

        // Nothing listening: the export stays queued, marked failed, with a later retry
        assertThat(sender.send(reload(item))).isFalse();
        ResultsOutboxItem failed = reload(item);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).isNotBlank();
        assertThat(failed.getNextAttemptAt()).isAfter(Instant.now().plusSeconds(20));

        List<Received> received = startRaceHub(port, 202);
        assertThat(sender.send(reload(item))).isTrue();

        ResultsOutboxItem sent = reload(item);
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).authorization()).isEqualTo("Bearer club-token");
        assertThat(json(received.get(0).body()).at("/revision").asLong()).isEqualTo(item.getRevision());
    }

    @Test
    void sendingTheSameExportTwiceIsSafe() throws IOException {
        importEntries();
        finishedRace(entryId("a1-" + run), walkIn("Walk-in Wendy"));
        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();
        int port = freePort();
        List<Received> received = startRaceHub(port, 200);
        ResultsSender sender = sender(port);

        assertThat(sender.send(reload(item))).isTrue();
        assertThat(sender.send(reload(item))).isTrue();

        // The same key and the same body each time, so RaceHub can recognise the replay
        assertThat(received).hasSize(2);
        assertThat(received.get(0).idempotencyKey()).isEqualTo("rctc-results-evt-" + run + "-r" + item.getRevision());
        assertThat(received.get(1).idempotencyKey()).isEqualTo(received.get(0).idempotencyKey());
        assertThat(received.get(1).body()).isEqualTo(received.get(0).body());
    }

    @Test
    void aRefusedExportIsRetriedLaterWithTheReason() throws IOException {
        importEntries();
        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.DAY_CLOSE).orElseThrow();
        int port = freePort();
        startRaceHub(port, 503);

        assertThat(sender(port).send(reload(item))).isFalse();

        ResultsOutboxItem failed = reload(item);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getLastError()).startsWith("RaceHub answered 503");
    }

    @Test
    void adminsCanListExportsRetryAndDownloadAnEventsResults() {
        importEntries();
        finishedRace(entryId("a1-" + run), walkIn("Walk-in Wendy"));
        ResultsOutboxItem item = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();

        ResponseEntity<JsonNode> list = restTemplate.exchange("/api/v1/admin/results-exports", HttpMethod.GET,
                new HttpEntity<>(headers()), JsonNode.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().get("sendingEnabled").asBoolean()).isFalse();
        assertThat(list.getBody().get("missingSettings").toString())
                .contains("the RaceHub address").contains("rctiming.racehub.token");
        assertThat(list.getBody().get("exports").findValuesAsText("eventName")).contains("Results export " + run);

        ResponseEntity<Void> retry = restTemplate.exchange("/api/v1/admin/results-exports/" + item.getId() + "/retry",
                HttpMethod.POST, new HttpEntity<>(headers()), Void.class);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var retried = jdbc.queryForList("select * from audit_log where action = 'RESULTS_EXPORT_RETRIED' "
                + "and entity_id = ?", String.valueOf(item.getId()));
        assertThat(retried).hasSize(1);
        assertThat(retried.get(0).get("actor_user_id")).isNotNull();

        ResponseEntity<String> download = restTemplate.exchange("/api/v1/admin/events/" + eventId + "/results-export",
                HttpMethod.GET, new HttpEntity<>(headers()), String.class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("results-event-" + eventId + "-r1.json");
        assertThat(json(download.getBody()).at("/races/0/results/0/entry_id").asText()).isEqualTo("a1-" + run);
    }

    @Test
    void adminsCanSetAndClearTheRaceHubAddress() {
        String url = "https://racehub-" + run + ".example/api/results";
        ResponseEntity<JsonNode> set = restTemplate.exchange("/api/v1/admin/results-exports/address", HttpMethod.PUT,
                new HttpEntity<>(Map.of("resultsUrl", url), headers()), JsonNode.class);
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(set.getBody().get("resultsUrl").asText()).isEqualTo(url);
        assertThat(set.getBody().get("savedUrl").asText()).isEqualTo(url);
        assertThat(jdbc.queryForList("select * from audit_log where action = 'RACEHUB_RESULTS_URL_CHANGED'"))
                .isNotEmpty();

        // Plain http to another machine would send the key in the clear
        ResponseEntity<String> refused = restTemplate.exchange("/api/v1/admin/results-exports/address",
                HttpMethod.PUT, new HttpEntity<>(Map.of("resultsUrl", "http://racehub.example/results"), headers()),
                String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(clubProfileService.getRaceHubResultsUrl()).contains(url);

        ResponseEntity<JsonNode> cleared = restTemplate.exchange("/api/v1/admin/results-exports/address",
                HttpMethod.PUT, new HttpEntity<>(Map.of("resultsUrl", " "), headers()), JsonNode.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("savedUrl").isNull()).isTrue();
        assertThat(clubProfileService.getRaceHubResultsUrl()).isEmpty();
    }

    @Test
    void anAbandonedRaceNamesTheRaceHubClassesOfItsGrid() {
        importEntries();
        long raceId = race("RUNNING");
        onGrid(raceId, entryId("a1-" + run));

        restTemplate.exchange("/api/v1/race-control/race/" + raceId + "/abandon",
                HttpMethod.POST, new HttpEntity<>(headers()), Void.class);

        JsonNode race = json(awaitOutbox(1).get(0).getPayload()).at("/races/0");
        assertThat(race.at("/results")).isEmpty();
        assertThat(race.at("/racehub_event_class_ids/0").asText()).isEqualTo("rh-class-buggy-" + run);
    }

    @Test
    void aRequestedExportIsQueuedEvenIfTheAppStoppedBeforeBuildingIt() {
        importEntries();
        // As left by a day close whose transaction committed just before the app stopped
        jdbc.update("update events set results_export_pending = 'DAY_CLOSE' where id = ?", eventId);

        assertThat(awaitOutbox(1).get(0).getReason()).isEqualTo(ExportReason.DAY_CLOSE);
        assertThat(jdbc.queryForObject("select results_export_pending from events where id = ?", String.class, eventId))
                .isNull();
    }

    @Test
    void aFailedSendDoesNotBringBackAnExportThatWasReplacedMeanwhile() throws IOException {
        importEntries();
        ResultsOutboxItem older = exportService.enqueue(eventId, ExportReason.RACE_FINISHED).orElseThrow();
        ResultsOutboxItem inFlight = reload(older);
        ResultsOutboxItem newer = exportService.enqueue(eventId, ExportReason.CORRECTION).orElseThrow();
        int port = freePort();
        startRaceHub(port, 503);

        // The sender still holds the older export it picked up before the newer one was queued
        assertThat(sender(port).send(inFlight)).isFalse();

        assertThat(reload(older).getStatus()).isEqualTo(OutboxStatus.SUPERSEDED);
        assertThat(reload(newer).getStatus()).isEqualTo(OutboxStatus.QUEUED);
    }

    @Test
    void anImportWithoutARaceHubEventIdIsRefused() throws IOException {
        String file = new ClassPathResource("racehub/entries-v1-initial.json").getContentAsString(StandardCharsets.UTF_8)
                .replace("{{run}}", run)
                .replace("\"evt-" + run + "\"", "\"\"");
        assertThat(file).doesNotContain("evt-" + run);

        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=false",
                HttpMethod.POST, new HttpEntity<>(file, headers()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("applied").asBoolean()).isFalse();
        assertThat(resp.getBody().toString()).contains("no RaceHub event id");
        assertThat(jdbc.queryForObject("select count(*) from entries where event_id = ?", Integer.class, eventId))
                .isZero();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private void penalty(long raceId, long entryId, String type, String value, String reason, long appliedAtMicros) {
        jdbc.update("insert into penalties (race_id, entry_id, penalty_type, value, reason, applied_by, applied_at) "
                + "values (?, ?, ?, ?, ?, 1, ?)", raceId, entryId, type, new java.math.BigDecimal(value), reason,
                appliedAtMicros);
    }

    private record Received(String authorization, String idempotencyKey, String body) {
    }

    private List<Received> startRaceHub(int port, int status) throws IOException {
        List<Received> received = new CopyOnWriteArrayList<>();
        raceHub = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        raceHub.createContext("/api/results", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"), body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        raceHub.start();
        return received;
    }

    private ResultsSender sender(int port) {
        var properties = new RaceHubResultsProperties(URI.create("http://127.0.0.1:" + port + "/api/results"), "club-token");
        return new ResultsSender(outboxRepository, eventRepository,
                new RaceHubResultsEndpoint(properties, clubProfileService), restClientBuilder);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private ResultsOutboxItem reload(ResultsOutboxItem item) {
        return outboxRepository.findById(item.getId()).orElseThrow();
    }

    private List<ResultsOutboxItem> outbox() {
        return outboxRepository.findAll().stream()
                .filter(i -> i.getEventId() == eventId)
                .sorted((a, b) -> Long.compare(a.getRevision(), b.getRevision()))
                .toList();
    }

    /** Waits for the exports the triggers queue on their own thread. */
    private List<ResultsOutboxItem> awaitOutbox(int count) {
        return await(() -> {
            List<ResultsOutboxItem> items = outbox();
            return items.size() >= count ? items : null;
        });
    }

    private static <T> T await(Supplier<T> check) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            T value = check.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Timed out waiting for the results export");
    }

    private void penalty(long raceId, long entryId, String type, int value, String reason) {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/penalty",
                HttpMethod.POST, new HttpEntity<>(Map.of("entryId", entryId, "penaltyType", type, "value", value,
                        "reason", reason), headers()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void importEntries() {
        String file;
        try {
            file = new ClassPathResource("racehub/entries-v1-initial.json").getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{run}}", run);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // Read the body as text so a failure says what the server answered. This set-up step failed once
        // in CI with only the status to go on (#117).
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/admin/events/" + eventId + "/racehub-import?dryRun=false",
                HttpMethod.POST, new HttpEntity<>(file, headers()), String.class);
        assertThat(resp.getStatusCode())
                .as("RaceHub import of the set-up entries, response body: %s", resp.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(json(resp.getBody()).get("applied").asBoolean())
                .as("RaceHub import of the set-up entries was applied, response body: %s", resp.getBody())
                .isTrue();
    }

    private long entryId(String externalEntryId) {
        return jdbc.queryForObject("select id from entries where external_source = 'RACEHUB' and external_entry_id = ?",
                Long.class, externalEntryId);
    }

    private long walkIn(String name) {
        long competitorId = jdbc.queryForObject("insert into competitors (display_name) values (?) returning id",
                Long.class, name);
        return jdbc.queryForObject("""
                insert into entries (event_id, event_class_id, competitor_id, transponder_number, status)
                values (?, ?, ?, ?, 'CONFIRMED') returning id""",
                Long.class, eventId, buggyClassId, competitorId, "9" + run);
    }

    private long race(String status) {
        return jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, final_letter,
                                   start_type, status, started_at)
                values (?, ?, 1, 1, 'A', 'GRID', ?, ?) returning id""",
                Long.class, roundId, buggyClassId, status, Instant.now().minusSeconds(300).toEpochMilli() * 1000);
    }

    /** A finished A final: the imported entry first with 12 laps, the walk-in second with 11. */
    private long finishedRace(long importedEntry, long walkInEntry) {
        long raceId = race("FINISHED");
        long now = Instant.now().toEpochMilli() * 1000;
        jdbc.update("update races set finished_at = ? where id = ?", now, raceId);
        String positions = """
                [{"position":1,"entryId":%d,"competitorId":null,"driverName":"Ada Lovelace","carNumber":"1",
                  "lapsCompleted":12,"totalTimeMs":300100,"bestLapMs":24000,"gapToLeaderMs":null},
                 {"position":2,"entryId":%d,"competitorId":null,"driverName":"Walk-in Wendy","carNumber":"2",
                  "lapsCompleted":11,"totalTimeMs":301900,"bestLapMs":25500,"gapToLeaderMs":null}]"""
                .formatted(importedEntry, walkInEntry);
        jdbc.update("insert into result_snapshots (race_id, finished_at, positions_json, timed_positions_json, "
                + "lap_history_json, created_at) values (?, ?, ?, ?, '[]', ?)", raceId, now, positions, positions, now);
        onGrid(raceId, importedEntry);
        onGrid(raceId, walkInEntry);
        return raceId;
    }

    private void onGrid(long raceId, long entryId) {
        jdbc.update("insert into race_entries (race_id, entry_id) values (?, ?)", raceId, entryId);
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
        String email = "results-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
