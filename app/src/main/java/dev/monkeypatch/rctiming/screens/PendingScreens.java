package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Screens that mDNS has found on the venue network but an official has not added yet (#708). Finding a device is not
 * trusting it: anyone on the network can advertise {@code _eventscreen._tcp}, and adding a screen is what lets this app
 * send it playlists and a saved token. So {@link ScreenControlService#discovered} parks an unknown screen here and
 * the Screens page offers it under "Found, not added" with an Add button. Held in memory only, because discovery
 * re-announces every screen every half minute; a screen not seen for {@value #KEEP_MINUTES} minutes drops out. Safe
 * to call from {@link ScreenDiscovery}'s thread and from request threads.
 */
@Component
public class PendingScreens {

    static final int KEEP_MINUTES = 5;
    /** Most screens remembered, so a noisy network cannot grow the list without bound. */
    static final int MAX = 100;

    /** A screen found but not added; the address is where it was last heard. */
    public record Found(String screenId, String name, String host, int port, String agentVersion,
                        boolean authRequired, Instant seenAt) {
    }

    private final Map<String, Found> found = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public PendingScreens() {
        this(Clock.systemUTC());
    }

    PendingScreens(Clock clock) {
        this.clock = clock;
    }

    /** Remembers (or refreshes) a screen that answered {@code /info}; one that reports no id is ignored. */
    void put(ViewerInfo info, String host, int port) {
        if (info.id() == null || info.id().isBlank() || info.id().length() > 100) {
            return;
        }
        prune();
        if (found.size() >= MAX && !found.containsKey(info.id())) {
            return;
        }
        String name = info.name() != null && !info.name().isBlank() ? info.name() : info.id();
        found.put(info.id(), new Found(info.id(), clip(name, 255), host, port, clip(info.version(), 50), info.authRequired(),
                clock.instant()));
    }

    /** The screens waiting to be added, by name. */
    public List<Found> list() {
        prune();
        return found.values().stream().sorted(Comparator.comparing(Found::name).thenComparing(Found::screenId)).toList();
    }

    /** Drops a screen once it has been added. */
    void forget(String screenId) {
        found.remove(screenId);
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private void prune() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(KEEP_MINUTES));
        found.values().removeIf(f -> f.seenAt().isBefore(cutoff));
    }
}
