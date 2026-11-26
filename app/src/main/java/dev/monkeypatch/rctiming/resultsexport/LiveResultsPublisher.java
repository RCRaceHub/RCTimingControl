package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.livefeed.LiveFeedRaceLookup;
import dev.monkeypatch.rctiming.livefeed.LiveFeedV1;
import dev.monkeypatch.rctiming.livefeed.LiveStandings;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends a running race's standings to RaceHub while the race is on, so RaceHub can show them live (Live Results v1,
 * {@code docs/live-results-v1.md}).
 *
 * <p>Runs on a thread of its own, once a second. For each race on the grid, running or stopped in an event imported
 * from RaceHub, it reads the running order timing already worked out and posts a {@link LiveResultsV1} snapshot to
 * RaceHub's {@code /api/rctc/v1/live}. A lap changes the order at once, but a race is sent at most every
 * {@link LiveResultsProperties#minIntervalMs()} (3 s by default), and again after
 * {@link LiveResultsProperties#resendEveryMs()} with no change so RaceHub knows the feed is alive.
 *
 * <p><strong>Lossy by design, and never in the way.</strong> A snapshot is the whole race as it stands, so a missed
 * one costs only a moment: nothing is queued or retried, and the next pass builds a fresh one. After a failure the
 * publisher waits 5 s, doubling to 60 s, before it tries again; timing, race control and the results outbox never
 * wait on it, and a slow RaceHub delays only this thread (3 s to connect, 5 s to read). Every snapshot is
 * provisional. The finished race still goes through Results Export v1, which stays authoritative; the last snapshot
 * of a race (sent once when it finishes or is restarted) only tells RaceHub the race is over.
 *
 * <p>Only events with a RaceHub event id are sent, and only while the club's publishing choice includes RaceHub and
 * the RaceHub address and key are set. A RaceHub that does not have the endpoint yet answers 404; that is treated like
 * any failure, so the publisher keeps trying quietly at the longest wait.
 *
 * <p><strong>Threads.</strong> Everything but the followed-race map is touched only on the {@code racehub-live}
 * thread. The map is filled from race control's threads by {@link #onRaceStatusChanged}.
 */
@Component
@EnableConfigurationProperties(LiveResultsProperties.class)
public class LiveResultsPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LiveResultsPublisher.class);

    static final Duration TICK = Duration.ofSeconds(1);
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(5);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final LiveResultsProperties properties;
    private final RaceHubResultsEndpoint endpoint;
    private final ResultsPublishSettings publishSettings;
    private final LiveFeedRaceLookup raceLookup;
    private final LapTimingService lapTimingService;
    private final RaceClockService raceClocks;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;

    /** Races followed from the grid to the finish. */
    private final Map<Long, Followed> races = new ConcurrentHashMap<>();
    /** Per race, what was last sent and when; only touched on the publisher's thread. */
    private final Map<Long, Sent> sent = new ConcurrentHashMap<>();
    private final AtomicLong sequence;

    private ScheduledExecutorService executor;
    private Instant nextAttempt = Instant.MIN;
    private Duration backoff = FIRST_BACKOFF;
    private boolean failing;

    @Autowired
    public LiveResultsPublisher(LiveResultsProperties properties, RaceHubResultsEndpoint endpoint,
                                ResultsPublishSettings publishSettings, LiveFeedRaceLookup raceLookup,
                                LapTimingService lapTimingService, RaceClockService raceClocks,
                                ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this(properties, endpoint, publishSettings, raceLookup, lapTimingService, raceClocks, objectMapper,
                restClientBuilder, Clock.systemUTC());
    }

    LiveResultsPublisher(LiveResultsProperties properties, RaceHubResultsEndpoint endpoint,
                         ResultsPublishSettings publishSettings, LiveFeedRaceLookup raceLookup,
                         LapTimingService lapTimingService, RaceClockService raceClocks, ObjectMapper objectMapper,
                         RestClient.Builder restClientBuilder, Clock clock) {
        this.properties = properties;
        this.endpoint = endpoint;
        this.publishSettings = publishSettings;
        this.raceLookup = raceLookup;
        this.lapTimingService = lapTimingService;
        this.raceClocks = raceClocks;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.clock = clock;
        this.sequence = new AtomicLong(clock.millis());
    }

    /**
     * Follows races from the grid to the finish. Runs synchronously on the thread that published the event, inside
     * the race command's transaction, so it only records what to follow; all reading and sending is on the
     * publisher's thread. A finish freezes the running order now, while timing still holds it, once the finish commits.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        long raceId = event.getRaceId();
        switch (event.getNewStatus()) {
            case GRID -> races.put(raceId, new Followed());
            case RUNNING, STOPPED -> races.computeIfAbsent(raceId, id -> new Followed());
            case FINISHED -> {
                List<LiveTimingRowDto> finalRows =
                        lapTimingService.peek(raceId).map(LiveRaceState::calculatePositions).orElse(List.of());
                AfterCommit.run(() -> races.computeIfAbsent(raceId, id -> new Followed()).freeze(finalRows));
            }
            default -> { }
        }
    }

    /** One pass; never throws. */
    void tick() {
        try {
            sendDue();
        } catch (RuntimeException e) {
            log.warn("Live results pass failed", e);
        }
    }

    private void sendDue() {
        boolean active = properties.enabled() && endpoint.liveUrl() != null && endpoint.token() != null
                && publishSettings.effective().includesRaceHub();
        Instant now = clock.instant();
        for (Map.Entry<Long, Followed> followed : races.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            long raceId = followed.getKey();
            Optional<LiveFeedRaceLookup.RaceInfo> found = raceLookup.find(raceId);
            if (found.isEmpty()) {
                drop(raceId);
                continue;
            }
            LiveFeedRaceLookup.RaceInfo race = found.get();
            boolean ended = RaceStatus.FINISHED.name().equals(race.status())
                    || RaceStatus.PENDING.name().equals(race.status());
            if (!active || race.racehubEventId() == null) {
                if (ended) {
                    drop(raceId);
                }
                continue;
            }
            if (now.isBefore(nextAttempt)) {
                continue;
            }
            Content content = content(race, followed.getValue());
            Sent last = sent.get(raceId);
            boolean changed = last == null || !last.content().sameAs(content);
            long sinceMs = last == null ? Long.MAX_VALUE : Duration.between(last.at(), now).toMillis();
            boolean due = last == null
                    || (changed && sinceMs >= properties.minIntervalMs())
                    || sinceMs >= properties.resendEveryMs();
            if (ended) {
                // One last snapshot says the race is over, then it is dropped whether or not it got through: the
                // finished race arrives through Results Export v1
                if (changed || last == null) {
                    post(race, content, now);
                }
                drop(raceId);
            } else if (due) {
                if (!post(race, content, now)) {
                    // Wait out the backoff before any race tries again
                    return;
                }
                sent.put(raceId, new Sent(content, now));
            }
        }
    }

    private void drop(long raceId) {
        races.remove(raceId);
        sent.remove(raceId);
    }

    private Content content(LiveFeedRaceLookup.RaceInfo race, Followed followed) {
        List<LiveTimingRowDto> rows = followed.frozenRows() != null ? followed.frozenRows()
                : lapTimingService.peek(race.raceId()).map(LiveRaceState::calculatePositions).orElse(List.of());
        RaceClockDto raceClock =
                raceClocks.clockOf(race.raceId(), RaceStatus.valueOf(race.status()), race.durationMs());
        return new Content(race.status(), LiveStandings.of(rows, race),
                new LiveFeedV1.Clock(raceClock.elapsedMs(), raceClock.durationMs(), raceClock.remainingMs(),
                        raceClock.running()));
    }

    /**
     * Posts one snapshot. Returns whether RaceHub accepted it (any 2xx). A failure starts or lengthens the wait before
     * the next attempt and is logged once per outage, so a RaceHub that is down does not fill the log.
     */
    private boolean post(LiveFeedRaceLookup.RaceInfo race, Content content, Instant now) {
        URI url = endpoint.liveUrl();
        String token = endpoint.token();
        if (url == null || token == null) {
            return false;
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(new LiveResultsV1(
                    LiveResultsV1.SCHEMA_VERSION, LiveResultsV1.TYPE, sequence.incrementAndGet(), now.toString(),
                    true, race.racehubEventId(), race.event(),
                    new LiveFeedV1.Race(race.raceId(), race.className(), race.roundType(), race.roundNumber(),
                            race.heatNumber(), race.finalLetter(), content.status(), content.clock()),
                    content.standings()));
        } catch (JacksonException e) {
            log.warn("Could not write a live results snapshot", e);
            return false;
        }
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.setBearerAuth(token);
                        endpoint.accessHeaders(url).forEach(h::set);
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            failed(now, "RaceHub answered " + e.getStatusCode().value());
            return false;
        } catch (RuntimeException e) {
            failed(now, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return false;
        }
        if (failing) {
            log.info("Live results are reaching RaceHub again");
        }
        failing = false;
        backoff = FIRST_BACKOFF;
        nextAttempt = Instant.MIN;
        return true;
    }

    private void failed(Instant now, String reason) {
        if (!failing) {
            log.warn("Live results not reaching RaceHub ({}); timing carries on, trying again shortly", reason);
        }
        failing = true;
        nextAttempt = now.plus(backoff);
        backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
    }

    /** Starts the {@code racehub-live} thread, ticking once a second. Idempotent. */
    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "racehub-live");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, TICK.toMillis(), TICK.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Stops the thread, interrupting a pass in flight. */
    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }

    /** A race followed, with its running order kept from the finish. */
    private static final class Followed {
        private List<LiveTimingRowDto> finalRows;

        synchronized void freeze(List<LiveTimingRowDto> rows) {
            finalRows = List.copyOf(rows);
        }

        synchronized List<LiveTimingRowDto> frozenRows() {
            return finalRows;
        }
    }

    /** What a snapshot says, without its sequence, send time or ticking clock, to tell whether anything changed. */
    private record Content(String status, List<LiveFeedV1.Standing> standings, LiveFeedV1.Clock clock) {
        boolean sameAs(Content other) {
            return status.equals(other.status) && standings.equals(other.standings)
                    && clock.running() == other.clock.running()
                    && Objects.equals(clock.durationMs(), other.clock.durationMs());
        }
    }

    private record Sent(Content content, Instant at) {
    }
}
