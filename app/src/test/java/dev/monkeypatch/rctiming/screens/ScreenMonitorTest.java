package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.ScreenState.Connection;
import dev.monkeypatch.rctiming.screens.viewer.ViewerClient;
import dev.monkeypatch.rctiming.screens.viewer.ViewerException;
import dev.monkeypatch.rctiming.screens.viewer.ViewerInfo;
import dev.monkeypatch.rctiming.screens.viewer.ViewerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the monitor turns polls into a state (#708): online, unreachable, offline, drift and warnings. */
class ScreenMonitorTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final ScreenRepository repository = mock(ScreenRepository.class);
    private final ViewerClient client = mock(ViewerClient.class);
    private final ScreenMonitor monitor = new ScreenMonitor(repository, client, Clock.fixed(NOW, ZoneOffset.UTC));
    private Screen screen;

    @BeforeEach
    void setUp() {
        screen = new Screen();
        screen.setId(7L);
        screen.setScreenId("screen-01");
        screen.setName("Pit");
        screen.setHost("10.0.0.5");
        screen.setPort(8080);
        screen.setApiVersion("1");
    }

    private static ViewerStatus status(long revision, String source, boolean pageReachable, boolean browserRunning,
                                       int restarts, double clock) {
        return new ViewerStatus("screen-01", 10.0, clock, "playing", false, 0, null, null,
                new ViewerStatus.Network(pageReachable, clock), new ViewerStatus.PlaylistSummary(revision, 2, 1.0, source),
                new ViewerStatus.Browser("cog", browserRunning, 1, "", restarts, ""), null, null);
    }

    private static double now() {
        return NOW.toEpochMilli() / 1000.0;
    }

    @Test
    void aScreenNeverPolledIsUnknown() {
        assertThat(monitor.stateOf(screen).connection()).isEqualTo(Connection.UNKNOWN);
    }

    @Test
    void aScreenThatAnswersIsOnlineWithItsStatus() {
        when(client.status(any())).thenReturn(status(4, "rctc:playlist-1", true, true, 0, now()));

        monitor.poll(screen);

        ScreenState state = monitor.stateOf(screen);
        assertThat(state.connection()).isEqualTo(Connection.ONLINE);
        assertThat(state.status().playlist().revision()).isEqualTo(4);
        assertThat(state.clockSkewSeconds()).isBetween(-0.01, 0.01);
        assertThat(state.warnings()).isEmpty();
    }

    @Test
    void aFewMissedPollsAreUnreachableAndManyAreOfflineAndTheLastSeenTimeIsWrittenOnce() {
        when(client.status(any())).thenReturn(status(4, "x", true, true, 0, now()));
        monitor.poll(screen);
        when(client.status(any())).thenThrow(new ViewerException(null, "Cannot reach the screen", null));

        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).connection()).isEqualTo(Connection.UNREACHABLE);
        assertThat(monitor.stateOf(screen).status()).isNotNull();
        for (int i = 1; i < ScreenMonitor.OFFLINE_AFTER_MISSES + 2; i++) {
            monitor.poll(screen);
        }

        assertThat(monitor.stateOf(screen).connection()).isEqualTo(Connection.OFFLINE);
        assertThat(monitor.stateOf(screen).error()).isEqualTo("Cannot reach the screen");
        verify(repository, times(1)).recordLastSeen(7L, NOW);
    }

    @Test
    void aScreenNeverSeenIsNotGivenALastSeenTime() {
        when(client.status(any())).thenThrow(new ViewerException(null, "Cannot reach the screen", null));

        for (int i = 0; i < ScreenMonitor.OFFLINE_AFTER_MISSES; i++) {
            monitor.poll(screen);
        }

        verify(repository, never()).recordLastSeen(any(), any());
    }

    @Test
    void aPlaylistChangedElsewhereIsDrift() {
        screen.setPushedRevision(4L);
        screen.setPushedSource("rctc:playlist-1");

        when(client.status(any())).thenReturn(status(4, "rctc:playlist-1", true, true, 0, now()));
        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).drifted()).isFalse();

        when(client.status(any())).thenReturn(status(5, "rctc:playlist-1", true, true, 0, now()));
        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).drifted()).isTrue();

        when(client.status(any())).thenReturn(status(4, "someone-else", true, true, 0, now()));
        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).drifted()).isTrue();
    }

    @Test
    void aScreenNeverPushedToIsNotDrift() {
        when(client.status(any())).thenReturn(status(9, "anything", true, true, 0, now()));

        monitor.poll(screen);

        assertThat(monitor.stateOf(screen).drifted()).isFalse();
    }

    @Test
    void problemsOnTheScreenBecomeWarnings() {
        when(client.status(any())).thenReturn(status(1, "", false, false, 3, now() + 60));

        monitor.poll(screen);

        assertThat(monitor.stateOf(screen).warnings()).hasSize(4)
                .anyMatch(w -> w.contains("cannot reach the page's server"))
                .anyMatch(w -> w.contains("browser is not running"))
                .anyMatch(w -> w.contains("restarted 3 time"))
                .anyMatch(w -> w.contains("ahead of"));
    }

    @Test
    void aScreenThatAsksForNoTokenIsWarnedAboutAndOneThatNeedsATokenWeLackIsToo() {
        when(client.status(any())).thenReturn(status(1, "", true, true, 0, now()));
        when(client.info(any())).thenReturn(new ViewerInfo("screen-01", "Pit", "pit", "0.1", "1", "cog", false, null, null));
        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).warnings()).singleElement().asString().contains("accepts changes from anyone");

        screen.setScreenId("screen-02");
        when(client.info(any())).thenReturn(new ViewerInfo("screen-02", "Bar", "bar", "0.1", "1", "cog", true, null, null));
        monitor.poll(screen);
        assertThat(monitor.stateOf(screen).warnings()).singleElement().asString().contains("needs a token");

        screen.setApiToken("encrypted");
        assertThat(monitor.stateOf(screen).warnings()).isEmpty();
    }
}
