package dev.monkeypatch.rctiming.query.boards;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RaceHighlightsTest {

    private static RaceHighlights.Run run(String name, Integer start, int position, Long... laps) {
        return new RaceHighlights.Run(name, start, position, Arrays.asList(laps));
    }

    @Test
    void biggestGainAndLossAreAgainstTheGrid() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                run("Ada", 4, 1), run("Bea", 1, 2), run("Cy", 2, 6), run("Di", 3, 3)));

        assertThat(scope.gained()).isEqualTo(new RaceHighlightsDto.Mark("Ada", 3));
        assertThat(scope.lost()).isEqualTo(new RaceHighlightsDto.Mark("Cy", 4));
    }

    @Test
    void nobodyGainingOrLosingLeavesTheMarkOut() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                run("Ada", 1, 1), run("Bea", 2, 2), run("Cy", null, 3)));

        assertThat(scope.gained()).isNull();
        assertThat(scope.lost()).isNull();
    }

    @Test
    void aTieGoesToTheFirstCarInTheList() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(run("Ada", 3, 1), run("Bea", 4, 2)));

        assertThat(scope.gained().driverName()).isEqualTo("Ada");
    }

    @Test
    void bestAverageIsTheQuickestRunOfLapsInARow() {
        List<Long> laps = List.of(20_000L, 10_000L, 11_000L, 12_000L, 30_000L, 9_000L);

        assertThat(RaceHighlights.bestAverage(laps, 3)).isEqualTo(11_000L);
        assertThat(RaceHighlights.bestAverage(laps, 5)).isEqualTo(14_400L);
        assertThat(RaceHighlights.bestAverage(laps, 7)).isNull();
    }

    @Test
    void anUnknownLapTimeBreaksTheRun() {
        List<Long> laps = Arrays.asList(10_000L, 10_000L, null, 10_000L, 10_000L);

        assertThat(RaceHighlights.bestAverage(laps, 3)).isNull();
        assertThat(RaceHighlights.bestAverage(laps, 2)).isEqualTo(10_000L);
    }

    @Test
    void theQuickestAverageAcrossCarsWins() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                run("Ada", 1, 1, 12_000L, 12_000L, 12_000L),
                run("Bea", 2, 2, 11_000L, 11_500L, 11_000L)));

        assertThat(scope.avg3()).isEqualTo(new RaceHighlightsDto.Mark("Bea", 11_167L));
        assertThat(scope.avg5()).isNull();
    }

    @Test
    void flyingLapIsTheBiggestMarginUnderTheDriversAverageSoFar() {
        // The slow first lap is ignored; the 9s lap beats the 11s average of the three laps before it by 2s
        List<Long> laps = List.of(30_000L, 11_000L, 11_000L, 11_000L, 9_000L, 10_500L);

        assertThat(RaceHighlights.flyingMargin(laps)).isEqualTo(2_000L);
        assertThat(RaceHighlights.flyingMargin(List.of(30_000L, 11_000L, 11_000L, 9_000L))).isNull();
        assertThat(RaceHighlights.flyingMargin(List.of(30_000L, 10_000L, 10_000L, 10_000L, 10_000L))).isNull();
    }

    @Test
    void lastLapFlyingIsTrueOnlyWhenTheLatestLapBeatsTheAverageBeforeIt() {
        assertThat(RaceHighlights.lastLapFlying(List.of(30_000L, 11_000L, 11_000L, 11_000L, 9_000L))).isTrue();
        // The next lap is slower, so the flame goes
        assertThat(RaceHighlights.lastLapFlying(List.of(30_000L, 11_000L, 11_000L, 11_000L, 9_000L, 10_500L))).isFalse();
        // Only two laps before it to beat, and the first is left out
        assertThat(RaceHighlights.lastLapFlying(List.of(30_000L, 11_000L, 11_000L, 9_000L))).isFalse();
        assertThat(RaceHighlights.lastLapFlying(List.of(30_000L, 10_000L, 10_000L, 10_000L, 10_000L))).isFalse();
        assertThat(RaceHighlights.lastLapFlying(List.of())).isFalse();
    }

    @Test
    void theBiggestFlyingMarginAcrossCarsWins() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                run("Ada", 1, 1, 30_000L, 11_000L, 11_000L, 11_000L, 10_500L),
                run("Bea", 2, 2, 30_000L, 11_000L, 11_000L, 11_000L, 9_000L)));

        assertThat(scope.flying()).isEqualTo(new RaceHighlightsDto.Mark("Bea", 2_000L));
    }

    @Test
    void consistencyNeedsEnoughLapsAndTheSmallestSpreadWins() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                run("Ada", 1, 1, 30_000L, 10_000L, 10_000L, 10_000L, 10_000L, 10_000L),
                run("Bea", 2, 2, 30_000L, 10_000L, 12_000L, 10_000L, 12_000L, 10_000L),
                // Perfectly even but too few laps to count
                run("Cy", 3, 3, 30_000L, 10_000L, 10_000L, 10_000L)));

        assertThat(scope.consistent()).isEqualTo(new RaceHighlightsDto.Mark("Ada", 0L));
        assertThat(RaceHighlights.spread(List.of(30_000L, 10_000L, 12_000L, 10_000L, 12_000L, 10_000L)))
                .isEqualTo(980L);
        assertThat(RaceHighlights.spread(List.of(30_000L, 10_000L, 10_000L, 10_000L))).isNull();
    }

    @Test
    void improvementIsLapsMoreThanThePreviousHeatAndNeedsAPreviousHeat() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                new RaceHighlights.Run("Ada", 1, 1, List.of(10_000L, 10_000L, 10_000L, 10_000L), 3),
                new RaceHighlights.Run("Bea", 2, 2, List.of(10_000L, 10_000L, 10_000L, 10_000L), 1),
                new RaceHighlights.Run("Cy", 3, 3, List.of(10_000L, 10_000L, 10_000L, 10_000L), 6),
                run("Di", 4, 4, 10_000L, 10_000L, 10_000L, 10_000L)));

        assertThat(scope.improved()).isEqualTo(new RaceHighlightsDto.Mark("Bea", 3L));
    }

    @Test
    void noOneImprovingLeavesTheMarkOut() {
        RaceHighlightsDto.Scope scope = RaceHighlights.of(List.of(
                new RaceHighlights.Run("Ada", 1, 1, List.of(10_000L, 10_000L), 2)));

        assertThat(scope.improved()).isNull();
    }
}
