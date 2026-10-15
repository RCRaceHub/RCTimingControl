package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sends queued results exports to RaceHub (#27). Runs on a scheduler thread, so race control never waits on
 * the network. A failed send is tried again with a growing delay, up to {@link #MAX_BACKOFF}; exports of one
 * event go in revision order, and a newer one replaces any that have not gone yet.
 *
 * <p>A failed export holds back only its own event: the rest of the pass carries on, so one event RaceHub
 * refuses does not stop the others reaching it. Whatever the answer, the export is tried again later; a
 * refusal is never given up on, and its reason stays on the export for the admin to read.
 *
 * <p>The scheduler has several threads ({@code spring.task.scheduling.pool.size}), so a slow RaceHub (5 s to
 * connect, 20 s to read) does not delay the decoder check, the announcements or the backup. This job and the
 * queuing in {@code ResultsExportTriggers} may therefore run together; the outbox's updates are guarded to
 * allow for it.
 *
 * <p>Each request carries an {@code Idempotency-Key} made from the event and revision, and the body carries the
 * revision, so sending the same export twice is safe.
 */
@Component
public class ResultsSender {

    static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final int MAX_ERROR_LENGTH = 500;

    private static final Logger log = LoggerFactory.getLogger(ResultsSender.class);

    private final ResultsOutboxRepository outboxRepository;
    private final EventRepository eventRepository;
    private final RaceHubResultsEndpoint endpoint;
    private final RestClient restClient;

    public ResultsSender(ResultsOutboxRepository outboxRepository, EventRepository eventRepository,
                         RaceHubResultsEndpoint endpoint, RestClient.Builder restClientBuilder) {
        this.outboxRepository = outboxRepository;
        this.eventRepository = eventRepository;
        this.endpoint = endpoint;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Scheduled(initialDelay = 15_000, fixedDelayString = "${rctiming.racehub.send-interval-ms:15000}")
    public void sendDue() {
        if (!endpoint.sendingEnabled()) {
            return;
        }
        List<ResultsOutboxItem> due = outboxRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
                EnumSet.of(OutboxStatus.QUEUED, OutboxStatus.FAILED), Instant.now());
        // Events whose export failed in this pass: their later exports wait, so an event's exports never go
        // out of order, but other events carry on
        Set<Long> heldBack = new HashSet<>();
        for (ResultsOutboxItem item : due) {
            if (heldBack.contains(item.getEventId())) {
                continue;
            }
            try {
                if (!send(item)) {
                    heldBack.add(item.getEventId());
                }
            } catch (RuntimeException e) {
                // Recording the outcome failed (the database); the next pass sends it again, which is safe
                log.error("Could not record the send of results export revision {} of event {}",
                        item.getRevision(), item.getEventId(), e);
                heldBack.add(item.getEventId());
            }
        }
    }

    /** Sends one export and records the outcome. Returns whether it was accepted. */
    boolean send(ResultsOutboxItem item) {
        String key = idempotencyKey(item);
        try {
            restClient.post()
                    .uri(endpoint.resultsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.setBearerAuth(endpoint.token());
                        h.set("Idempotency-Key", key);
                    })
                    .body(item.getPayload())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            return failed(item, "RaceHub answered " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            return failed(item, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        outboxRepository.recordSent(item.getId(), Instant.now());
        log.info("Sent results export revision {} of event {} to RaceHub", item.getRevision(), item.getEventId());
        return true;
    }

    private boolean failed(ResultsOutboxItem item, String error) {
        int attempts = item.getAttempts() + 1;
        String lastError = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        outboxRepository.recordFailure(item.getId(), lastError, Instant.now().plus(backoff(attempts)));
        log.warn("Results export revision {} of event {} not sent (attempt {}): {}",
                item.getRevision(), item.getEventId(), attempts, lastError);
        return false;
    }

    /** 30 s after the first failure, doubling each time, never more than 30 minutes. */
    static Duration backoff(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 16);
        Duration delay = FIRST_BACKOFF.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    /** Stable for one revision of one RaceHub event, whichever laptop sends it and however often. */
    private String idempotencyKey(ResultsOutboxItem item) {
        String racehubEventId = eventRepository.findById(item.getEventId())
                .map(Event::getRacehubEventId)
                .orElse("rctc-event-" + item.getEventId());
        return "rctc-results-" + racehubEventId + "-r" + item.getRevision();
    }
}
