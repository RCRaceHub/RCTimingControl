package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A car that stops counting laps is not waited for when the race ends, and is a DNF. */
class LiveRaceStateStalledTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
    }

    private void pass(long entryId, long seconds, boolean clockExpired) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId,
                seconds * 1_000L, clockExpired);
    }

    /** Car 1 and car 2 each run 10 s laps up to 50 s; car 2 then stops. */
    private void carTwoStopsAfterFifty() {
        for (long t = 0; t <= 50; t += 10) {
            pass(1, t, false);
            pass(2, t + 1, false);
        }
        for (long t = 60; t <= 100; t += 10) {
            pass(1, t, false);
        }
    }

    @Test
    void aCarWithNoLapForFourAverageLapsIsStalled() {
        carTwoStopsAfterFifty();

        assertThat(state.isStalled(2, 100_000)).isTrue();
        assertThat(state.isStalled(1, 100_000)).isFalse();
    }

    @Test
    void aCarThatIsOnlyAFewSecondsLateIsNotStalled() {
        carTwoStopsAfterFifty();

        assertThat(state.isStalled(2, 70_000)).isFalse();
    }

    @Test
    void theRaceEndsWhenEveryCarStillRunningHasTakenTheFlag() {
        carTwoStopsAfterFifty();
        pass(1, 110, true);

        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void theStoppedCarIsADnfOnceTheRaceHasReachedItsEnd() {
        carTwoStopsAfterFifty();
        assertThat(state.isDnf(2, 100_000)).isFalse();

        pass(1, 110, true);
        assertThat(state.isDnf(2, 110_000)).isTrue();
        assertThat(state.isDnf(1, 110_000)).isFalse();
    }

    @Test
    void aStoppedCarThatTakesTheFlagIsNotADnf() {
        carTwoStopsAfterFifty();
        pass(1, 110, true);
        pass(2, 112, true);

        assertThat(state.isDnf(2, 113_000)).isFalse();
    }

    @Test
    void turningTheCheckOffWaitsForEveryCar() {
        state.setStalledAfterLaps(0);
        carTwoStopsAfterFifty();
        pass(1, 110, true);

        assertThat(state.takeFinishRequest()).isFalse();
    }

    @Test
    void aPauseIsNotHeldAgainstTheCars() {
        carTwoStopsAfterFifty();
        state.pauseStallClocks(70_000);
        // Stopped at 70 s, finished at 400 s: the cars are judged as of the stop
        assertThat(state.isStalled(2, 400_000)).isFalse();

        state.restartStallClocks(400_000);
        assertThat(state.isStalled(2, 400_000)).isFalse();
        assertThat(state.isStalled(2, 480_000)).isTrue();
    }

    @Test
    void aCarMarkedDidNotStartThatCrossedOnceIsNotWaitedFor() {
        // Car 3 rolled over the line once at the start, was then marked DNS, and never moved again
        pass(3, 1, false);
        for (long t = 0; t <= 50; t += 10) {
            pass(1, t, false);
        }
        pass(1, 60, true);
        pass(1, 70, true);
        state.setNotRacing(3, true);

        // Judged on its clock alone it has not stopped yet (19 s), but it is out of the race
        assertThat(state.allFlagged(20_000)).isTrue();
    }

    @Test
    void aCarThatRacesAgainIsWaitedForAgain() {
        pass(3, 1, false);
        for (long t = 0; t <= 50; t += 10) {
            pass(1, t, false);
        }
        pass(1, 60, true);
        state.setNotRacing(3, true);
        state.setNotRacing(3, false);

        assertThat(state.allFlagged(20_000)).isFalse();
    }

    @Test
    void aLinkedCarThatThenStopsIsNotWaitedForUntilTheTimeout() {
        // Car 1 runs 10 s laps; transponder T9 is unknown until car 2 is linked to it, after its last lap
        for (long t = 0; t <= 50; t += 10) {
            pass(1, t, false);
        }
        state.applyLapPassing(new LapPassingEvent(1L, "T9", 5_000_000L), null, 5_000L, false);
        state.applyLapPassing(new LapPassingEvent(1L, "T9", 15_000_000L), null, 15_000L, false);
        state.applyLapPassing(new LapPassingEvent(1L, "T9", 25_000_000L), null, 25_000L, false);
        for (long t = 60; t <= 100; t += 10) {
            pass(1, t, false);
        }
        state.retroactiveLinkTransponder("T9", 2L);
        pass(1, 110, true);

        // 40 s after the linked car's latest lap it is judged stopped, as any other car would be
        assertThat(state.takeFinishRequest()).isFalse();
        assertThat(state.allFlagged(160_000)).isTrue();
    }
}
