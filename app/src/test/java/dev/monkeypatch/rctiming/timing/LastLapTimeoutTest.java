package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LastLapTimeoutTest {

    @Test
    void waitsWhileTheClockHasOnlyJustExpired() {
        assertThat(LastLapTimeout.timedOut(5_000, null, 30_000)).isFalse();
    }

    @Test
    void finishesWhenNoLapHasCountedSinceTheClockExpired() {
        assertThat(LastLapTimeout.timedOut(31_000, null, 30_000)).isTrue();
    }

    @Test
    void aLapCountingAfterTheClockExpiredRestartsTheWait() {
        // Expired 60 s ago, but a car crossed 10 s ago
        assertThat(LastLapTimeout.timedOut(60_000, 10_000L, 30_000)).isFalse();
    }

    @Test
    void aLapFromBeforeTheClockExpiredDoesNotShortenTheWait() {
        // The last lap counted 90 s ago, before the clock expired 5 s ago
        assertThat(LastLapTimeout.timedOut(5_000, 90_000L, 30_000)).isFalse();
    }

    @Test
    void finishesOnceTheTrackHasBeenQuietForTheWholeWindow() {
        assertThat(LastLapTimeout.timedOut(60_000, 31_000L, 30_000)).isTrue();
    }
}
