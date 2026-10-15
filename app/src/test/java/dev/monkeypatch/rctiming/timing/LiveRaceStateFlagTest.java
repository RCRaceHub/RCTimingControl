package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRaceStateFlagTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
    }

    private void pass(long entryId, long seconds, boolean clockExpired) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId,
                seconds * 1_000L, clockExpired);
    }

    private int laps(long entryId) {
        return state.getPositionSnapshot(entryId).getLapsCompleted();
    }

    @Test
    void aCarTakesTheFlagOnItsFirstLapCountedAfterTheClockRanOut() {
        pass(1, 10, false);
        pass(1, 40, true);
        pass(1, 70, true);

        assertThat(laps(1)).isEqualTo(1);
    }

    @Test
    void aFirstPassingAfterTheClockRanOutDoesNotTakeTheFlag() {
        pass(1, 40, true);
        assertThat(state.takeFinishRequest()).isFalse();

        pass(1, 70, true);
        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void theRaceIsNotOverUntilEveryCarHasTakenTheFlag() {
        pass(1, 10, false);
        pass(2, 12, false);
        pass(1, 40, true);
        assertThat(state.takeFinishRequest()).isFalse();

        pass(2, 43, true);
        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void theFinishIsAskedForOnce() {
        pass(1, 10, false);
        pass(1, 40, true);

        assertThat(state.takeFinishRequest()).isTrue();
        assertThat(state.takeFinishRequest()).isFalse();
    }

    @Test
    void nothingIsFlaggedWhileTheClockIsStillRunning() {
        pass(1, 10, false);
        pass(1, 40, false);

        assertThat(state.takeFinishRequest()).isFalse();
    }

    @Test
    void aPassingTooSoonToCountDoesNotTakeTheFlag() {
        state.putMinLaps(java.util.Map.of(1L, 10_000L));
        pass(1, 10, false);
        pass(1, 12, true);
        assertThat(state.takeFinishRequest()).isFalse();

        pass(1, 40, true);
        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void aCarThatNeverCrossedIsNotWaitedFor() {
        state.applyLapDelta(9, 1);
        pass(1, 10, false);
        pass(1, 40, true);

        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void noRaceIsOverBeforeAnyCarHasPassed() {
        assertThat(state.takeFinishRequest()).isFalse();
    }

    @Test
    void aCarThatTookTheFlagIsMarkedFinishedInLiveTiming() {
        pass(1, 10, false);
        pass(2, 12, false);
        pass(1, 40, true);

        assertThat(state.calculatePositions()).extracting(r -> r.entryId() + ":" + r.finished())
                .containsExactlyInAnyOrder("1:true", "2:false");
    }
}
