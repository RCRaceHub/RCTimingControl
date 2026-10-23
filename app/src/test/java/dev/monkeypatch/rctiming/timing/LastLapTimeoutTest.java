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

    @Test
    void theMaximumRaceWindowIsReachedOnceTheRaceHasRunThatLong() {
        // A 5 minute race with a 6:30 window: 89 s after the clock expired is not yet, 90 s is
        assertThat(LastLapTimeout.windowReached(89_000, 300_000, 390_000)).isFalse();
        assertThat(LastLapTimeout.windowReached(90_000, 300_000, 390_000)).isTrue();
    }

    @Test
    void theCountdownIsWhatIsLeftOfTheWaitTheTimeoutJudges() {
        // Clock expired 10 s ago with no lap since: 20 s of a 30 s wait remain
        assertThat(LastLapTimeout.waitLeftMs(10_000, null, 30_000)).isEqualTo(20_000);
        // A lap 4 s ago puts the wait back to 26 s
        assertThat(LastLapTimeout.waitLeftMs(60_000, 4_000L, 30_000)).isEqualTo(26_000);
        assertThat(LastLapTimeout.waitLeftMs(60_000, 40_000L, 30_000)).isZero();
    }
}
