package dev.monkeypatch.rctiming.resultsexport;

import com.sun.net.httpserver.HttpServer;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One send pass over several events' exports (#136): a refused event does not hold up the others. */
class ResultsSenderPassTest {

    private final ResultsOutboxRepository outbox = mock(ResultsOutboxRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final List<String> requestedKeys = new CopyOnWriteArrayList<>();
    private HttpServer raceHub;

    @AfterEach
    void stopRaceHub() {
        if (raceHub != null) {
            raceHub.stop(0);
        }
    }

    @Test
    void aRefusedEventDoesNotStopTheOthersAndItsOwnLaterExportsWait() throws IOException {
        ResultsOutboxItem refused = item(1, 10, 1);
        ResultsOutboxItem behindIt = item(2, 10, 2);
        ResultsOutboxItem otherEvent = item(3, 20, 1);
        due(refused, behindIt, otherEvent);

        // RaceHub refuses event 10 and accepts the rest
        ResultsSender sender = senderTo(key -> key.contains("evt-10") ? 422 : 202);
        sender.sendDue();

        // Event 10's first export was tried and refused; its second was not tried; event 20 still went
        assertThat(requestedKeys).containsExactly("rctc-results-evt-10-r1", "rctc-results-evt-20-r1");
        verify(outbox).recordFailure(eq(1L), anyString(), any(Instant.class));
        verify(outbox).recordSent(eq(3L), any(Instant.class));
        verify(outbox, never()).recordSent(eq(2L), any(Instant.class));
        verify(outbox, never()).recordFailure(eq(2L), anyString(), any(Instant.class));
    }

    @Test
    void aDatabaseFailureRecordingOneSendDoesNotStopTheOtherEvents() throws IOException {
        ResultsOutboxItem first = item(1, 10, 1);
        ResultsOutboxItem behindIt = item(2, 10, 2);
        ResultsOutboxItem otherEvent = item(3, 20, 1);
        due(first, behindIt, otherEvent);
        // RaceHub accepts every export, but the outcome of event 10's cannot be written
        when(outbox.recordSent(eq(1L), any(Instant.class))).thenThrow(new IllegalStateException("database is locked"));

        senderTo(key -> 202).sendDue();

        // Event 10's later export waits for the next pass, which sends the first again (safe: same key);
        // event 20 is sent and recorded
        assertThat(requestedKeys).containsExactly("rctc-results-evt-10-r1", "rctc-results-evt-20-r1");
        verify(outbox).recordSent(eq(3L), any(Instant.class));
        verify(outbox, never()).recordSent(eq(2L), any(Instant.class));
    }

    private void due(ResultsOutboxItem... items) {
        when(outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(anyCollection(), any(Instant.class)))
                .thenReturn(List.of(items));
        when(events.findById(anyLong())).thenAnswer(inv -> {
            Event event = new Event();
            event.setRacehubEventId("evt-" + inv.<Long>getArgument(0));
            return Optional.of(event);
        });
    }

    /** A sender pointed at a stand-in RaceHub that answers each request with the status chosen from its key. */
    private ResultsSender senderTo(Function<String, Integer> statusForKey) throws IOException {
        raceHub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        raceHub.createContext("/api/results", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            requestedKeys.add(key);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(statusForKey.apply(key), -1);
            exchange.close();
        });
        raceHub.start();
        var properties = new RaceHubResultsProperties(
                URI.create("http://127.0.0.1:" + raceHub.getAddress().getPort() + "/api/results"), "club-token");
        return new ResultsSender(outbox, events,
                new RaceHubResultsEndpoint(properties, mock(ClubProfileService.class)), RestClient.builder());
    }

    private static ResultsOutboxItem item(long id, long eventId, long revision) {
        ResultsOutboxItem item = new ResultsOutboxItem();
        item.setId(id);
        item.setEventId(eventId);
        item.setRevision(revision);
        item.setPayload("{}");
        item.setStatus(OutboxStatus.QUEUED);
        return item;
    }
}
