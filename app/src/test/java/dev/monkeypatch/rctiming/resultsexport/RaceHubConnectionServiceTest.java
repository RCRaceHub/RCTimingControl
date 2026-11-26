package dev.monkeypatch.rctiming.resultsexport;

import com.sun.net.httpserver.HttpServer;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The RaceHub connection test and the director's status, against a stand-in RaceHub on this machine. */
class RaceHubConnectionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);

    private final ResultsOutboxQuery outbox = mock(ResultsOutboxQuery.class);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer raceHub;

    @BeforeEach
    void outboxHoldsTwoWaitingAndOneFailed() {
        when(outbox.summary()).thenReturn(new ResultsOutboxQuery.Summary(2, 1, null, "boom"));
    }

    @AfterEach
    void stopRaceHub() {
        if (raceHub != null) {
            raceHub.stop(0);
        }
    }

    @Test
    void aWorkingKeyPassesEveryCheckAndCountsTheReleasedMeetings() throws IOException {
        RaceHubConnectionService service = service("club-key", 200, "{\"events\":[{\"id\":\"a\"},{\"id\":\"b\"}]}", 422);

        var result = service.test();

        assertThat(result.ok()).isTrue();
        assertThat(result.readyEvents()).isEqualTo(2);
        assertThat(result.checks()).extracting(RaceHubConnectionService.Check::state)
                .containsOnly(RaceHubConnectionService.CheckState.PASS);
        // The results route was only asked with an empty body, so nothing was stored
        assertThat(requests).containsExactly("GET /api/rctc/v1/events", "POST /api/rctc/v1/results {}");
    }

    @Test
    void aRevokedKeyIsReportedAsRefusedAndTheResultsCheckIsSkipped() throws IOException {
        RaceHubConnectionService service = service("old-key", 401, "{\"message\":\"Unauthenticated.\"}", 401);

        var result = service.test();

        assertThat(result.ok()).isFalse();
        assertThat(result.summary()).contains("refused the key (401)");
        assertThat(check(result, "reach").state()).isEqualTo(RaceHubConnectionService.CheckState.PASS);
        assertThat(check(result, "read").state()).isEqualTo(RaceHubConnectionService.CheckState.FAIL);
        assertThat(check(result, "write").state()).isEqualTo(RaceHubConnectionService.CheckState.SKIPPED);
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.KEY_REFUSED);
    }

    @Test
    void aKeyWithoutResultsWriteIsToldSo() throws IOException {
        RaceHubConnectionService service = service("read-only", 200, "[]", 403);

        var result = service.test();

        assertThat(result.ok()).isFalse();
        assertThat(check(result, "read").state()).isEqualTo(RaceHubConnectionService.CheckState.PASS);
        assertThat(check(result, "write").state()).isEqualTo(RaceHubConnectionService.CheckState.FAIL);
        assertThat(check(result, "write").detail()).contains("results:write");
    }

    @Test
    void aPageThatIsNotRaceHubIsNotTakenForOne() throws IOException {
        RaceHubConnectionService service = service("club-key", 200, "<html>Sign in</html>", 200, "text/html");

        var result = service.test();

        assertThat(result.ok()).isFalse();
        assertThat(check(result, "read").detail()).contains("not like RaceHub");
    }

    @Test
    void anAddressNobodyAnswersAtIsUnreachable() {
        RaceHubConnectionService service = new RaceHubConnectionService(
                endpoint("http://127.0.0.1:1", "club-key"), outbox, JsonMapper.builder().build(), RestClient.builder(), CLOCK);

        var result = service.test();

        assertThat(result.ok()).isFalse();
        assertThat(check(result, "reach").state()).isEqualTo(RaceHubConnectionService.CheckState.FAIL);
        assertThat(check(result, "read").state()).isEqualTo(RaceHubConnectionService.CheckState.SKIPPED);
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.UNREACHABLE);
    }

    @Test
    void withNoKeyNothingIsSentAndTheStatusIsHidden() {
        RaceHubConnectionService service = new RaceHubConnectionService(
                endpoint("https://racehub.example", null), outbox, JsonMapper.builder().build(), RestClient.builder(), CLOCK);

        var result = service.test();

        assertThat(result.ok()).isFalse();
        assertThat(check(result, "key").state()).isEqualTo(RaceHubConnectionService.CheckState.FAIL);
        assertThat(check(result, "reach").state()).isEqualTo(RaceHubConnectionService.CheckState.SKIPPED);
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.NOT_CONFIGURED);
    }

    @Test
    void theBackgroundCheckMakesTheStatusOkWithoutTouchingTheResultsRoute() throws IOException {
        RaceHubConnectionService service = service("club-key", 200, "[]", 422);
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.CHECKING);

        service.refresh();

        var status = service.status();
        assertThat(status.health()).isEqualTo(RaceHubConnectionService.Health.OK);
        assertThat(status.lastOkAt()).isEqualTo(CLOCK.instant());
        assertThat(status.waiting()).isEqualTo(2);
        assertThat(status.lastSendError()).isEqualTo("boom");
        assertThat(requests).containsExactly("GET /api/rctc/v1/events");
    }

    @Test
    void aReadOnlyKeyReadsTheSameWhicheverCheckRanLast() throws IOException {
        RaceHubConnectionService service = service("read-only", 200, "[]", 403);

        service.test();
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.OK);
        service.refresh();
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.OK);
    }

    @Test
    void aSlowDownAnswerDoesNotTurnTheIndicatorRed() throws IOException {
        RaceHubConnectionService service = service("club-key", 429, "{}", 429);

        service.refresh();

        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.OK);
    }

    @Test
    void aChangedKeyMakesTheLastCheckStale() throws IOException {
        var props = new java.util.concurrent.atomic.AtomicReference<>("one");
        raceHub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        raceHub.createContext("/api/rctc/v1/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("[]".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        raceHub.start();
        String base = "http://127.0.0.1:" + raceHub.getAddress().getPort();
        RaceHubKeyStore store = mock(RaceHubKeyStore.class);
        when(store.saved()).thenAnswer(i -> java.util.Optional.of(props.get()));
        var endpoint = new RaceHubResultsEndpoint(new RaceHubResultsProperties(null, null, null, null, URI.create(base)),
                mock(ClubProfileService.class), store);
        var service = new RaceHubConnectionService(endpoint, outbox, JsonMapper.builder().build(), RestClient.builder(), CLOCK);

        service.refresh();
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.OK);
        props.set("two");
        assertThat(service.status().health()).isEqualTo(RaceHubConnectionService.Health.CHECKING);
    }

    private static RaceHubConnectionService.Check check(RaceHubConnectionService.TestResult result, String id) {
        return result.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private RaceHubConnectionService service(String key, int eventsStatus, String eventsBody, int resultsStatus)
            throws IOException {
        return service(key, eventsStatus, eventsBody, resultsStatus, "application/json");
    }

    private RaceHubConnectionService service(String key, int eventsStatus, String eventsBody, int resultsStatus,
                                             String contentType) throws IOException {
        raceHub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        raceHub.createContext("/api/rctc/v1/events", exchange -> {
            requests.add("GET " + exchange.getRequestURI().getPath());
            byte[] body = eventsBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(eventsStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        raceHub.createContext("/api/rctc/v1/results", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add("POST " + exchange.getRequestURI().getPath() + " " + body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(resultsStatus, -1);
            exchange.close();
        });
        raceHub.start();
        return new RaceHubConnectionService(endpoint("http://127.0.0.1:" + raceHub.getAddress().getPort(), key),
                outbox, JsonMapper.builder().build(), RestClient.builder(), CLOCK);
    }

    private static RaceHubResultsEndpoint endpoint(String base, String key) {
        return new RaceHubResultsEndpoint(new RaceHubResultsProperties(null, key, null, null, URI.create(base)),
                mock(ClubProfileService.class));
    }
}
