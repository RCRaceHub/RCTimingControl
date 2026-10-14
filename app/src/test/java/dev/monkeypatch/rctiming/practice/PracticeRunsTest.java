package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A pause between laps starts a new run, and the pause is not a lap (#151). */
class PracticeRunsTest {

    private static final long SECOND = 1_000_000L;

    @Test
    void aPassingWithinThePauseIsALapOfTheSameRun() {
        LivePracticeState state = new LivePracticeState(3, 120);

        state.notePassing("T1", 100 * SECOND);
        LivePracticeState.Passing second = state.notePassing("T1", 130 * SECOND);

        assertThat(second.lapTimeMs()).isEqualTo(30_000L);
        assertThat(second.runNumber()).isEqualTo(1);
    }

    @Test
    void theFirstPassingAfterALongPauseStartsANewRunWithNoLap() {
        LivePracticeState state = new LivePracticeState(3, 120);

        state.notePassing("T1", 100 * SECOND);
        state.notePassing("T1", 130 * SECOND);
        LivePracticeState.Passing afterPause = state.notePassing("T1", 400 * SECOND);
        LivePracticeState.Passing next = state.notePassing("T1", 430 * SECOND);

        assertThat(afterPause.lapTimeMs()).isNull();
        assertThat(afterPause.runNumber()).isEqualTo(2);
        assertThat(next.lapTimeMs()).isEqualTo(30_000L);
        assertThat(next.runNumber()).isEqualTo(2);
    }

    @Test
    void eachTransponderHasItsOwnRuns() {
        LivePracticeState state = new LivePracticeState(3, 120);

        state.notePassing("T1", 100 * SECOND);
        state.notePassing("T2", 110 * SECOND);
        state.notePassing("T1", 400 * SECOND);

        assertThat(state.notePassing("T2", 140 * SECOND).runNumber()).isEqualTo(1);
    }

    @Test
    void theBestConsecutiveLapsAreNeverTakenAcrossARunBoundary() {
        LivePracticeState state = new LivePracticeState(2, 120);
        // Two runs of two laps each: 2 laps of 30s, then 2 laps of 40s. A window across the boundary would be 70s.
        state.recordLap("T1", null, "Racer", 30_000L, 1);
        state.recordLap("T1", null, "Racer", 30_000L, 1);
        state.recordLap("T1", null, "Racer", 40_000L, 2);
        state.recordLap("T1", null, "Racer", 40_000L, 2);

        PracticeTimingRowDto row = state.calculatePositions().get(0);

        assertThat(row.bestConsecutiveNMs()).isEqualTo(60_000L);
        assertThat(row.laps()).isEqualTo(4);
        assertThat(row.runs()).isEqualTo(2);
    }

    @Test
    void noWindowIsReportedWhenNoRunHasEnoughLaps() {
        LivePracticeState state = new LivePracticeState(3, 120);
        state.recordLap("T1", null, "Racer", 30_000L, 1);
        state.recordLap("T1", null, "Racer", 30_000L, 1);
        state.recordLap("T1", null, "Racer", 30_000L, 2);

        assertThat(state.calculatePositions().get(0).bestConsecutiveNMs()).isNull();
    }
}
