package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.LapOrder.LapClock;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The lap history records who was where after each lap, by the order the cars crossed the line (position chart). */
class LapOrderTest {

    private static LiveTimingRowDto row(long entryId, int finalPosition, int laps, long lastPassing) {
        return new LiveTimingRowDto(entryId, "Driver " + entryId, String.valueOf(entryId), finalPosition, laps,
                lastPassing, null, null, null, null, 0, 0, null, null, true);
    }

    @Test
    void aCarThatPassedAnotherOnLapTwoIsFirstOnlyFromThen() {
        // Car 1 is quicker on lap 1 (10 s v 11 s), car 2 on lap 2 (9 s v 12 s): car 1 leads after lap 1, car 2 after lap 2
        long start = 1_000_000;
        List<LiveTimingRowDto> rows = List.of(
                row(2, 1, 2, start + 11_000 + 9_000),
                row(1, 2, 2, start + 10_000 + 12_000));
        Map<Long, LapClock> clocks = Map.of(
                1L, new LapClock(start + 22_000, List.of(10_000L, 12_000L)),
                2L, new LapClock(start + 20_000, List.of(11_000L, 9_000L)));

        assertThat(LapOrder.positionsAfterLap(1, rows, clocks)).containsEntry(1L, 1).containsEntry(2L, 2);
        assertThat(LapOrder.positionsAfterLap(2, rows, clocks)).containsEntry(2L, 1).containsEntry(1L, 2);
    }

    @Test
    void aCarThatHasNotCompletedTheLapIsNotPlacedForIt() {
        long start = 1_000_000;
        List<LiveTimingRowDto> rows = List.of(
                row(1, 1, 2, start + 20_000),
                row(2, 2, 1, start + 15_000));
        Map<Long, LapClock> clocks = Map.of(
                1L, new LapClock(start + 20_000, List.of(10_000L, 10_000L)),
                2L, new LapClock(start + 15_000, List.of(15_000L)));

        assertThat(LapOrder.positionsAfterLap(2, rows, clocks)).containsOnlyKeys(1L).containsEntry(1L, 1);
    }

    @Test
    void withoutUsableLapTimesEveryCarKeepsItsFinalPosition() {
        List<LiveTimingRowDto> rows = List.of(row(1, 1, 2, 5), row(2, 2, 2, 6));
        // Car 2's lap count was changed by a marshal adjustment: two laps counted but one lap time recorded
        Map<Long, LapClock> clocks = Map.of(
                1L, new LapClock(5, List.of(2L, 3L)),
                2L, new LapClock(6, List.of(6L)));

        assertThat(LapOrder.positionsAfterLap(1, rows, clocks)).containsEntry(1L, 1).containsEntry(2L, 2);
        assertThat(LapOrder.positionsAfterLap(1, rows, Map.of())).containsEntry(1L, 1).containsEntry(2L, 2);
    }

    @Test
    void placesListsEveryCarForEveryLapItCompleted() {
        List<LiveTimingRowDto> rows = List.of(row(1, 1, 2, 22_000), row(2, 2, 1, 15_000));

        // No live state: the places fall back to the final order and the lap times are unknown
        LapOrder order = LapOrder.of(rows, null);

        assertThat(order.leaderLaps()).isEqualTo(2);
        assertThat(order.places()).containsExactly(
                new LapOrder.Place(1, 1, 1), new LapOrder.Place(1, 2, 2), new LapOrder.Place(2, 1, 1));
        assertThat(order.lapTimeMs(1, 1)).isNull();
    }
}
