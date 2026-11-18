package dev.monkeypatch.rctiming.simulator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link TrackSimulation}: the loops must agree about where each car is. */
class TrackSimulationTest {

    private static final long LAP_MS = 10_000;

    private TrackSimulation track(long jitterMs) {
        return new TrackSimulation(List.of("101", "202"), LAP_MS, jitterMs, new double[] {0.3, 0.7}, 0, 42L);
    }

    @Test
    void everyLoopSeesEveryCarOncePerLap() {
        TrackSimulation track = track(1_000);

        for (int loop = 0; loop < track.loopCount(); loop++) {
            var crossings = track.crossings(loop, 0, 100_000);
            assertThat(crossings.stream().filter(c -> c.transponder().equals("101")).count())
                    .as("loop %d", loop).isBetween(6L, 12L);
        }
    }

    @Test
    void aSplitIsCrossedAfterTheFinishLoopAndBeforeTheNextFinishCrossing() {
        TrackSimulation track = track(1_000);
        var finish = track.crossings(0, 0, 60_000).stream().filter(c -> c.transponder().equals("101")).toList();
        var split1 = track.crossings(1, 0, 60_000).stream().filter(c -> c.transponder().equals("101")).toList();
        var split2 = track.crossings(2, 0, 60_000).stream().filter(c -> c.transponder().equals("101")).toList();

        for (int lap = 0; lap < split1.size() - 1; lap++) {
            assertThat(split1.get(lap).timeMs()).isGreaterThan(finish.get(lap).timeMs())
                    .isLessThan(split2.get(lap).timeMs());
            assertThat(split2.get(lap).timeMs()).isLessThan(finish.get(lap + 1).timeMs());
        }
    }

    @Test
    void withNoJitterTheSplitSitsAtItsShareOfTheLap() {
        TrackSimulation track = track(0);
        var finish = track.crossings(0, 0, 40_000).stream().filter(c -> c.transponder().equals("101")).toList();
        var split1 = track.crossings(1, 0, 40_000).stream().filter(c -> c.transponder().equals("101")).toList();

        double lap = finish.get(1).timeMs() - finish.get(0).timeMs();
        assertThat(split1.get(0).timeMs() - finish.get(0).timeMs()).isCloseTo(0.3 * lap, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void askingForTheSameWindowTwiceGivesTheSameCrossings() {
        TrackSimulation track = track(1_000);

        assertThat(track.crossings(1, 5_000, 30_000)).isEqualTo(track.crossings(1, 5_000, 30_000));
    }

    @Test
    void crossingsComeOldestFirst() {
        var times = track(1_000).crossings(0, 0, 50_000).stream().map(TrackSimulation.Crossing::timeMs).toList();

        assertThat(times).isSorted();
    }
}
