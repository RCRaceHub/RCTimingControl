package dev.monkeypatch.rctiming.livefeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import dev.monkeypatch.rctiming.timing.dto.LiveFeedStatusDto;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends live timing to the relay for remote viewers (#28).
 *
 * <p>Runs on a thread of its own, once a second: for each race on the grid or running in an event with the feed
 * on, it reads the running order the live timing already worked out and sends a Live Feed v1 message over one
 * outbound WebSocket. A race is sent again when its running order or status changes, and every few seconds
 * besides so viewers joining late catch up. The feed only ever reads timing state, and a slow or missing relay
 * only delays this thread, so it can never hold up timing or race control. A dropped connection is reopened
 * with a growing wait.
 */
@Component
@EnableConfigurationProperties(LiveFeedProperties.class)
public class LiveFeedPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedPublisher.class);

    static final Duration TICK = Duration.ofSeconds(1);
    /** Each race is sent at least this often while it is followed, so its clock and late joiners stay current. */
    static final Duration RESEND_EVERY = Duration.ofSeconds(5);
    /** With nothing to send for this long, the connection is closed. */
    static final Duration IDLE_CLOSE_AFTER = Duration.ofMinutes(2);
    static final Duration FIRST_RETRY = Duration.ofSeconds(1);
    static final Duration MAX_RETRY = Duration.ofSeconds(30);

    private final LiveFeedProperties properties;
    private final LiveFeedRaceLookup raceLookup;
    private final LapTimingService lapTimingService;
    private final RaceClockService raceClocks;
    private final LiveTimingHub liveTimingHub;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LiveFeedConnection connection;

    /** Races followed: on the grid, running or stopped, or finished with the last message still to go. */
    private final Map<Long, Followed> races = new ConcurrentHashMap<>();
    /** Per race, what was last sent and when; only touched on the feed's thread. */
    private final Map<Long, Sent> sent = new ConcurrentHashMap<>();
    /** Starts from the clock, so it keeps going up across restarts and viewers never mistake a new message for an old one. */
    private final AtomicLong sequence;

    private volatile LiveFeedState state;
    private ScheduledExecutorService executor;
    private Instant nextAttempt = Instant.MIN;
    private Duration retryDelay = FIRST_RETRY;
    private Instant lastActivity;

    @Autowired
    public LiveFeedPublisher(LiveFeedProperties properties, LiveFeedRaceLookup raceLookup,
                             LapTimingService lapTimingService, RaceClockService raceClocks,
                             LiveTimingHub liveTimingHub, ObjectMapper objectMapper) {
        this(properties, raceLookup, lapTimingService, raceClocks, liveTimingHub, objectMapper, Clock.systemUTC(),
                new LiveFeedConnection());
    }

    LiveFeedPublisher(LiveFeedProperties properties, LiveFeedRaceLookup raceLookup,
                      LapTimingService lapTimingService, RaceClockService raceClocks, LiveTimingHub liveTimingHub,
                      ObjectMapper objectMapper, Clock clock, LiveFeedConnection connection) {
        this.properties = properties;
        this.raceLookup = raceLookup;
        this.lapTimingService = lapTimingService;
        this.raceClocks = raceClocks;
        this.liveTimingHub = liveTimingHub;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.connection = connection;
        this.state = properties.configured() ? LiveFeedState.IDLE : LiveFeedState.NOT_SET_UP;
        this.lastActivity = clock.instant();
        this.sequence = new AtomicLong(clock.millis());
    }

    public LiveFeedStatusDto status() {
        return new LiveFeedStatusDto(state.name(),
                properties.relayUrl() == null ? null : properties.relayUrl().getHost(),
                properties.missingSettings());
    }

    LiveFeedState state() {
        return state;
    }

    private void setState(LiveFeedState newState) {
        if (state != newState) {
            state = newState;
            try {
                liveTimingHub.broadcastLiveFeedStatus(status());
            } catch (RuntimeException e) {
                log.debug("Could not broadcast the live feed status", e);
            }
        }
    }

    /** Follows races from the grid to the finish; the race clock itself is kept by {@link RaceClockService}. */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        long raceId = event.getRaceId();
        switch (event.getNewStatus()) {
            case GRID -> races.put(raceId, new Followed());
            case RUNNING, STOPPED -> races.computeIfAbsent(raceId, id -> new Followed());
            // Keep the final running order: finishing stores the result and lets the live state go straight
            // after this event, before the feed's next pass
            case FINISHED -> races.computeIfAbsent(raceId, id -> new Followed())
                    .freeze(lapTimingService.peek(raceId).map(LiveRaceState::calculatePositions).orElse(List.of()));
            default -> { }
        }
    }

    /** One pass: work out what to send, connect if needed, and send it. Never throws. */
    void tick() {
        try {
            sendDue();
        } catch (RuntimeException e) {
            log.warn("Live feed pass failed", e);
        }
    }

    private void sendDue() {
        if (!properties.configured()) {
            setState(LiveFeedState.NOT_SET_UP);
            return;
        }
        Instant now = clock.instant();
        List<Outgoing> due = collect(now);
        if (due.isEmpty()) {
            if (connection.isOpen() && Duration.between(lastActivity, now).compareTo(IDLE_CLOSE_AFTER) > 0) {
                connection.close();
            }
            if (!connection.isOpen()) {
                setState(LiveFeedState.IDLE);
                retryDelay = FIRST_RETRY;
                nextAttempt = Instant.MIN;
            }
            return;
        }
        lastActivity = now;

        if (!connection.isOpen() && !connect(now)) {
            return;
        }
        for (Outgoing message : due) {
            try {
                connection.send(message.json());
            } catch (Exception e) {
                failed(now, "sending", e);
                return;
            }
            sent.put(message.raceId(), new Sent(message.content(), now));
            if (message.last()) {
                races.remove(message.raceId());
                sent.remove(message.raceId());
            }
        }
    }

    private boolean connect(Instant now) {
        if (now.isBefore(nextAttempt)) {
            return false;
        }
        if (state != LiveFeedState.RECONNECTING) {
            setState(LiveFeedState.CONNECTING);
        }
        try {
            connection.open(properties.relayUrl(), properties.token());
        } catch (Exception e) {
            failed(now, "connecting to", e);
            return false;
        }
        log.info("Live feed connected to {}", properties.relayUrl().getHost());
        retryDelay = FIRST_RETRY;
        nextAttempt = Instant.MIN;
        // A new connection may be a new relay, or one that restarted: send every race in full
        sent.clear();
        setState(LiveFeedState.CONNECTED);
        return true;
    }

    private void failed(Instant now, String doing, Exception e) {
        connection.close();
        if (state != LiveFeedState.RECONNECTING) {
            log.warn("Live feed: failed {} the relay ({}); timing carries on, trying again shortly", doing,
                    describe(e));
        }
        setState(LiveFeedState.RECONNECTING);
        nextAttempt = now.plus(retryDelay);
        retryDelay = retryDelay.multipliedBy(2).compareTo(MAX_RETRY) > 0 ? MAX_RETRY : retryDelay.multipliedBy(2);
    }

    /** The messages due this pass, oldest race first. Drops races that need no more sending. */
    private List<Outgoing> collect(Instant now) {
        List<Outgoing> due = new ArrayList<>();
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
            if (!race.feedOn()) {
                if (ended) {
                    drop(raceId);
                }
                continue;
            }
            Content content = content(race, followed.getValue());
            Sent last = sent.get(raceId);
            boolean changed = last == null || !last.content().sameAs(content);
            boolean stale = last == null || Duration.between(last.at(), now).compareTo(RESEND_EVERY) >= 0;
            if (changed || stale) {
                due.add(new Outgoing(raceId, content, json(race, content, now), ended));
            } else if (ended) {
                drop(raceId);
            }
        }
        return due;
    }

    private void drop(long raceId) {
        races.remove(raceId);
        sent.remove(raceId);
    }

    private Content content(LiveFeedRaceLookup.RaceInfo race, Followed followed) {
        List<LiveTimingRowDto> rows = followed.frozenRows() != null ? followed.frozenRows()
                : lapTimingService.peek(race.raceId()).map(LiveRaceState::calculatePositions).orElse(List.of());
        List<LiveFeedV1.Standing> standings = new ArrayList<>();
        Set<Long> timed = new HashSet<>();
        for (LiveTimingRowDto r : rows) {
            timed.add(r.entryId());
            standings.add(new LiveFeedV1.Standing(r.position(), r.driverName(), race.carNumber(r.entryId()),
                    r.lapsCompleted(), r.lastLapMs(), r.bestLapMs(), r.gapToLeaderMs(), r.gapToAheadMs(),
                    r.lapsDown()));
        }
        // Cars on the grid that haven't crossed the line yet follow, in grid order
        int leaderLaps = rows.isEmpty() ? 0 : rows.get(0).lapsCompleted();
        for (LiveFeedRaceLookup.GridCar car : race.grid()) {
            if (car.displayName() != null && !timed.contains(car.entryId())) {
                standings.add(new LiveFeedV1.Standing(standings.size() + 1, car.displayName(), car.carNumber(),
                        0, null, null, null, null, leaderLaps));
            }
        }
        RaceClockDto raceClock =
                raceClocks.clockOf(race.raceId(), RaceStatus.valueOf(race.status()), race.durationMs());
        LiveFeedV1.Clock raceTime = new LiveFeedV1.Clock(raceClock.elapsedMs(), raceClock.durationMs(),
                raceClock.remainingMs(), raceClock.running());
        return new Content(race.status(), standings, raceTime);
    }

    private String json(LiveFeedRaceLookup.RaceInfo race, Content content, Instant now) {
        LiveFeedV1 message = new LiveFeedV1(
                LiveFeedV1.SCHEMA_VERSION,
                LiveFeedV1.TYPE_RACE,
                sequence.incrementAndGet(),
                now.toString(),
                race.event(),
                new LiveFeedV1.Race(race.raceId(), race.className(), race.roundType(), race.roundNumber(),
                        race.heatNumber(), race.finalLetter(), content.status(), content.clock()),
                content.standings());
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write a live feed message", e);
        }
    }

    private static String describe(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "live-feed");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, TICK.toMillis(), TICK.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        connection.close();
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }

    /** A race the feed follows, with its running order kept from the finish. */
    private static final class Followed {
        private List<LiveTimingRowDto> finalRows;

        synchronized void freeze(List<LiveTimingRowDto> rows) {
            finalRows = List.copyOf(rows);
        }

        synchronized List<LiveTimingRowDto> frozenRows() {
            return finalRows;
        }
    }

    /** What a message says, without its sequence, send time or ticking clock, to tell whether anything changed. */
    private record Content(String status, List<LiveFeedV1.Standing> standings, LiveFeedV1.Clock clock) {
        boolean sameAs(Content other) {
            return status.equals(other.status) && standings.equals(other.standings)
                    && clock.running() == other.clock.running()
                    && Objects.equals(clock.durationMs(), other.clock.durationMs());
        }
    }

    private record Sent(Content content, Instant at) {
    }

    private record Outgoing(long raceId, Content content, String json, boolean last) {
    }
}
