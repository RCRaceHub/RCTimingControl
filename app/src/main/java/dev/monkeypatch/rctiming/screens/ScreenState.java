package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerStatus;

import java.time.Instant;
import java.util.List;

/**
 * What the manager knows about a screen right now, built by {@link ScreenMonitor#stateOf}.
 *
 * @param connection       how recent polls went
 * @param status           the last status the screen gave, or null if it has never answered since the app started
 * @param statusAt         when that status was read
 * @param error            why the last poll failed, or null
 * @param clockSkewSeconds the screen's clock minus this computer's, in seconds, or null if unknown
 * @param drifted          true when the playlist on the screen is not the one the manager last pushed
 * @param features         what the screen's agent says it offers (from its info), or null until that has been read
 * @param warnings         things an official should look at, as sentences: page not reachable, browser down, and so on
 */
public record ScreenState(Connection connection, ViewerStatus status, Instant statusAt, String error,
                          Double clockSkewSeconds, boolean drifted, List<String> features, List<String> warnings) {

    /**
     * {@code ONLINE} answered the last poll; {@code UNREACHABLE} missed a few (Wi-Fi drops briefly, so it is not gone
     * yet); {@code OFFLINE} missed many; {@code UNKNOWN} has not been polled yet.
     */
    public enum Connection { ONLINE, UNREACHABLE, OFFLINE, UNKNOWN }
}
