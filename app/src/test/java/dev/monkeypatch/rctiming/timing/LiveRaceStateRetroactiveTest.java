package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRaceStateRetroactiveTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
    }

    @Test
    void retroactiveLinkCreditsAllPassingsForTransponder() {
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-1", 1_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-1", 2_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-1", 3_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "OTHER", 1_500_000L), 999L);

        List<LiveTimingRowDto> positions = state.retroactiveLinkTransponder("UNKNOWN-1", 100L);

        LiveTimingRowDto entry100 = positions.stream()
                .filter(p -> p.entryId() == 100L)
                .findFirst()
                .orElseThrow();
        assertThat(entry100.lapsCompleted()).isEqualTo(2);
    }

    @Test
    void retroactiveLinkRemovesTransponderFromUnknownSet() {
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-2", 1_000_000L), null);
        assertThat(state.seenUnknownTransponders).contains("UNKNOWN-2");

        state.retroactiveLinkTransponder("UNKNOWN-2", 200L);

        assertThat(state.seenUnknownTransponders).doesNotContain("UNKNOWN-2");
    }

    @Test
    void retroactiveLinkReturnsRecalculatedPositions() {
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 1_000_000L), 1L);
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 2_000_000L), 1L);
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-3", 1_100_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-3", 2_100_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "UNKNOWN-3", 3_100_000L), null);

        List<LiveTimingRowDto> positions = state.retroactiveLinkTransponder("UNKNOWN-3", 2L);

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).entryId()).isEqualTo(2L);
        assertThat(positions.get(0).position()).isEqualTo(1);
    }

    @Test
    void retroactiveLinkOnEmptyHistoryIsNoop() {
        List<LiveTimingRowDto> positions = state.retroactiveLinkTransponder("NONEXISTENT", 300L);
        assertThat(positions).isEmpty();
    }

    @Test
    void retroactiveLinkPreservesLapHistoryOrder() {
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 1_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 3_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 5_000_000L), null);

        int initialHistorySize = state.lapHistory.size();
        state.retroactiveLinkTransponder("T1", 1L);

        assertThat(state.lapHistory).hasSize(initialHistorySize);
    }

    @Test
    void countPassingsForTransponderReturnsCorrectCount() {
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 1_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 2_000_000L), null);
        state.applyLapPassing(new LapPassingEvent(1L, "T2", 1_500_000L), null);

        assertThat(state.countPassingsForTransponder("T1")).isEqualTo(2);
        assertThat(state.countPassingsForTransponder("T2")).isEqualTo(1);
        assertThat(state.countPassingsForTransponder("NONEXISTENT")).isZero();
    }
}

