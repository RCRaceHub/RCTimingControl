package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerClient;
import dev.monkeypatch.rctiming.screens.viewer.ViewerException;
import dev.monkeypatch.rctiming.screens.viewer.ViewerStatus;
import dev.monkeypatch.rctiming.screens.viewer.ViewerTarget;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the live status of every known screen (#708). The screens have no push, so a scheduler thread polls {@code GET
 * /status} on each every few seconds, all at once on virtual threads so one dead screen (a 2 s connect timeout) never
 * delays the rest. Readings live in memory only; the app starts with {@code UNKNOWN} and fills in within one poll.
 *
 * <p>A screen that misses a poll is {@code UNREACHABLE}, not gone: Wi-Fi drops briefly, so only after {@value
 * #OFFLINE_AFTER_MISSES} misses in a row (about half a minute) is it {@code OFFLINE}, and then its last-seen time is
 * written once. Never throws out of the scheduler. Request threads read states with {@link #stateOf} and feed the
 * status a command returned back in with {@link #record}.
 */
@Component
public class ScreenMonitor {

    /** Polls missed in a row before a screen counts as offline. */
    public static final int OFFLINE_AFTER_MISSES = 6;
    /** A screen clock further than this from ours is worth a warning, in seconds. */
    static final double SKEW_WARNING_SECONDS = 5;

    private static final Logger log = LoggerFactory.getLogger(ScreenMonitor.class);

    private record Reading(ViewerStatus status, Instant statusAt, Double skew, int misses, String error, Instant lastOk) { }

    private final ScreenRepository screens;
    private final ViewerClient client;
    private final Clock clock;
    private final ExecutorService pollers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Reading> readings = new ConcurrentHashMap<>();

    @Autowired
    public ScreenMonitor(ScreenRepository screens, ViewerClient client) {
        this(screens, client, Clock.systemUTC());
    }

    ScreenMonitor(ScreenRepository screens, ViewerClient client, Clock clock) {
        this.screens = screens;
        this.client = client;
        this.clock = clock;
    }

    /** One pass over every screen, in parallel; waits for all of them (each is bounded by the client's timeouts). */
    @Scheduled(initialDelay = 5_000, fixedDelayString = "${rctiming.screens.poll-interval-ms:5000}")
    public void pollAll() {
        try {
            List<Screen> all = screens.findAllByName();
            readings.keySet().removeIf(id -> all.stream().noneMatch(s -> s.getScreenId().equals(id)));
            List<Callable<Void>> tasks = new ArrayList<>();
            for (Screen screen : all) {
                tasks.add(() -> {
                    poll(screen);
                    return null;
                });
            }
            pollers.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Screen poll failed: {}", e.getMessage());
        }
    }

    /** Reads one screen's status now and records the outcome, hit or miss. Blocks up to the client's timeouts. */
    public void poll(Screen screen) {
        try {
            ViewerStatus status = client.status(new ViewerTarget(screen.getHost(), screen.getPort(), null));
            record(screen, status);
        } catch (ViewerException e) {
            miss(screen, e.getMessage());
        } catch (RuntimeException e) {
            log.debug("Unexpected failure polling screen {}", screen.getScreenId(), e);
            miss(screen, "Could not read the screen's status");
        }
    }

    /** Takes in a status the screen has just returned, from a poll or from a command. */
    public void record(Screen screen, ViewerStatus status) {
        Instant now = clock.instant();
        Double skew = status.time() == null ? null : status.time() - now.toEpochMilli() / 1000.0;
        readings.put(screen.getScreenId(), new Reading(status, now, skew, 0, null, now));
    }

    private void miss(Screen screen, String error) {
        Reading previous = readings.get(screen.getScreenId());
        int misses = previous == null ? 1 : previous.misses() + 1;
        Instant lastOk = previous == null ? null : previous.lastOk();
        readings.put(screen.getScreenId(), new Reading(previous == null ? null : previous.status(),
                previous == null ? null : previous.statusAt(), previous == null ? null : previous.skew(), misses, error, lastOk));
        if (misses == OFFLINE_AFTER_MISSES && lastOk != null) {
            try {
                screens.recordLastSeen(screen.getId(), lastOk);
            } catch (RuntimeException e) {
                log.warn("Could not record when screen {} was last seen: {}", screen.getScreenId(), e.getMessage());
            }
        }
    }

    /** The state of one screen from the latest reading and what the manager last pushed to it. */
    public ScreenState stateOf(Screen screen) {
        Reading r = readings.get(screen.getScreenId());
        if (r == null) {
            return new ScreenState(ScreenState.Connection.UNKNOWN, null, null, null, null, false, List.of());
        }
        ScreenState.Connection connection = r.misses() == 0 ? ScreenState.Connection.ONLINE
                : r.misses() < OFFLINE_AFTER_MISSES ? ScreenState.Connection.UNREACHABLE : ScreenState.Connection.OFFLINE;
        ViewerStatus status = r.status();
        boolean online = connection == ScreenState.Connection.ONLINE;
        return new ScreenState(connection, status, r.statusAt(), r.error(), online ? r.skew() : null,
                online && drifted(screen, status), warnings(screen, status, online ? r.skew() : null, connection));
    }

    /** True when the screen has been pushed to and what it holds now differs in revision or source. */
    static boolean drifted(Screen screen, ViewerStatus status) {
        if (screen.getPushedRevision() == null || status == null || status.playlist() == null) {
            return false;
        }
        ViewerStatus.PlaylistSummary held = status.playlist();
        return !Objects.equals(held.revision(), screen.getPushedRevision())
                || !Objects.equals(blankToNull(held.source()), blankToNull(screen.getPushedSource()));
    }

    private static List<String> warnings(Screen screen, ViewerStatus status, Double skew, ScreenState.Connection connection) {
        List<String> warnings = new ArrayList<>();
        if (screen.getApiVersion() != null && !screen.getApiVersion().equals("1")) {
            warnings.add("The screen speaks API version " + screen.getApiVersion() + "; this manager speaks version 1");
        }
        if (status != null && connection == ScreenState.Connection.ONLINE) {
            if (status.network() != null && Boolean.FALSE.equals(status.network().pageReachable())) {
                warnings.add("The screen cannot reach the page's server. Check the address in the playlist is one the "
                        + "screens can open, not localhost");
            }
            if (status.browser() != null && !status.browser().running()) {
                warnings.add("The screen's browser is not running");
            }
            if (status.browser() != null && status.browser().restarts() != null && status.browser().restarts() > 0) {
                warnings.add("The screen's browser has restarted " + status.browser().restarts() + " time(s)");
            }
        }
        if (skew != null && Math.abs(skew) > SKEW_WARNING_SECONDS) {
            warnings.add("The screen's clock is " + Math.round(Math.abs(skew)) + " s " + (skew > 0 ? "ahead of" : "behind")
                    + " this computer");
        }
        return warnings;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @PreDestroy
    void stop() {
        pollers.shutdownNow();
    }
}
