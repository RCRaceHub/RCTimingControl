package dev.monkeypatch.rctiming.timing.battle;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BattleTrackerTest {

    private static LiveTimingRowDto row(long entryId, int position, int laps, Long gapToAheadMs) {
        return row(entryId, position, laps, gapToAheadMs, 0, false);
    }

    private static LiveTimingRowDto row(long entryId, int position, int laps, Long gapToAheadMs,
                                        int intervalLapsDown, boolean finished) {
        return new LiveTimingRowDto(entryId, "D" + entryId, null, position, laps, 0L, null, null, null, null,
                0, intervalLapsDown, null, gapToAheadMs, null, finished, 0L);
    }

    /** A leader and one chaser, one update per lap, the chaser's gap taken from {@code gaps}. */
    private static List<BattleGroupDto> lapsOfPair(BattleTracker tracker, long... gaps) {
        List<BattleGroupDto> last = List.of();
        for (int i = 0; i < gaps.length; i++) {
            last = tracker.advance(List.of(row(1, 1, 3 + i, null), row(2, 2, 3 + i, gaps[i])));
        }
        return last;
    }

    @Test
    void findsAPairWithinTheGapAndIgnoresTheLeaderAndWideGaps() {
        BattleTracker tracker = new BattleTracker(2000);

        List<BattleGroupDto> groups = tracker.advance(
                List.of(row(1, 1, 3, null), row(2, 2, 3, 1500L), row(3, 3, 3, 4000L)));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).cars()).extracting(BattleCarDto::entryId).containsExactly(1L, 2L);
        assertThat(groups.get(0).links().get(0).trend()).isEqualTo(BattleTrend.UNKNOWN);
    }

    @Test
    void reportsClosingAndGainingFastFromTheAverageChangePerLap() {
        BattleLinkDto link = lapsOfPair(new BattleTracker(2000), 1800, 1500, 1200, 900).get(0).links().get(0);

        assertThat(link.ratePerLapMs()).isEqualTo(-300L);
        assertThat(link.trend()).isEqualTo(BattleTrend.CLOSING);
        assertThat(link.gainingFast()).isTrue();
    }

    @Test
    void reportsGrowingAndHolding() {
        assertThat(lapsOfPair(new BattleTracker(2000), 900, 1100, 1400).get(0).links().get(0).trend())
                .isEqualTo(BattleTrend.GROWING);
        assertThat(lapsOfPair(new BattleTracker(2000), 1000, 1050, 1000).get(0).links().get(0).trend())
                .isEqualTo(BattleTrend.HOLDING);
    }

    @Test
    void keepsAPairForTwoLapsAfterItDropsOutThenLetsItGo() {
        assertThat(lapsOfPair(new BattleTracker(2000), 1500, 2500, 3000)).hasSize(1);
        assertThat(lapsOfPair(new BattleTracker(2000), 1500, 2500, 3000, 3500)).isEmpty();
    }

    @Test
    void startsTheTrendAgainWhenAPairReturnsAfterALongGap() {
        List<BattleGroupDto> groups = lapsOfPair(new BattleTracker(2000),
                1500, 4000, 4000, 4000, 4000, 1800, 1400);

        assertThat(groups.get(0).links().get(0).ratePerLapMs()).isEqualTo(-400L);
    }

    @Test
    void startsTheTrendAgainWhenTheLapCountGoesBack() {
        BattleTracker tracker = new BattleTracker(2000);
        tracker.advance(List.of(row(1, 1, 3, null), row(2, 2, 3, 1500L)));
        tracker.advance(List.of(row(1, 1, 4, null), row(2, 2, 4, 1400L)));

        List<BattleGroupDto> groups = tracker.advance(List.of(row(1, 1, 5, null), row(2, 2, 3, 1300L)));

        assertThat(groups.get(0).links().get(0).trend()).isEqualTo(BattleTrend.UNKNOWN);
    }

    @Test
    void keepsAPairThroughTheMomentTheCarAheadHasCrossedAndTheChaserHasNot() {
        BattleTracker tracker = new BattleTracker(2000);
        long[] gaps = {1800, 1500, 1200, 900};
        List<BattleGroupDto> last = List.of();
        for (int i = 0; i < gaps.length; i++) {
            // The leader crosses first: the order has the chaser a lap down and no gap until it crosses too
            List<BattleGroupDto> between = tracker.advance(List.of(row(1, 1, 4 + i, null), row(2, 2, 3 + i, null, 1, false)));
            if (i > 0) {
                assertThat(between).hasSize(1);
            }
            last = tracker.advance(List.of(row(1, 1, 4 + i, null), row(2, 2, 4 + i, gaps[i])));
        }

        assertThat(last.get(0).links().get(0).ratePerLapMs()).isEqualTo(-300L);
    }

    @Test
    void ignoresALappedCarAFinishedCarAndAZeroGap() {
        assertThat(new BattleTracker(2000).advance(
                List.of(row(1, 1, 3, null), row(2, 2, 2, 500L, 1, false)))).isEmpty();
        assertThat(new BattleTracker(2000).advance(
                List.of(row(1, 1, 3, null), row(2, 2, 3, 500L, 0, true)))).isEmpty();
        assertThat(new BattleTracker(0).advance(List.of(row(1, 1, 3, null), row(2, 2, 3, 500L)))).isEmpty();
    }

    @Test
    void joinsChainedPairsIntoATrainAndKeepsSeparateFightsApart() {
        List<BattleGroupDto> groups = new BattleTracker(2000).advance(List.of(
                row(1, 1, 3, null), row(2, 2, 3, 800L), row(3, 3, 3, 1200L), row(4, 4, 3, 6000L), row(5, 5, 3, 900L)));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).cars()).extracting(BattleCarDto::entryId).containsExactly(1L, 2L, 3L);
        assertThat(groups.get(0).links()).hasSize(2);
        assertThat(groups.get(1).cars()).extracting(BattleCarDto::entryId).containsExactly(4L, 5L);
    }

    @Test
    void putsAGroupGainingFastFirst() {
        BattleTracker tracker = new BattleTracker(2000);
        List<BattleGroupDto> groups = List.of();
        long[] fast = {1800, 1500, 1200};
        long[] tight = {600, 600, 600};
        for (int i = 0; i < 3; i++) {
            groups = tracker.advance(List.of(
                    row(1, 1, 3 + i, null), row(2, 2, 3 + i, tight[i]),
                    row(3, 3, 3 + i, 9000L), row(4, 4, 3 + i, fast[i])));
        }

        assertThat(groups.get(0).gainingFast()).isTrue();
        assertThat(groups.get(0).cars()).extracting(BattleCarDto::entryId).containsExactly(3L, 4L);
    }
}
