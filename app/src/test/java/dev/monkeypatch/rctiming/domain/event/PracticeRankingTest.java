package dev.monkeypatch.rctiming.domain.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeRankingTest {

    private static final List<Long> LAPS = List.of(15_000L, 14_000L, 14_500L, 16_000L, 13_900L);

    @Test
    void fastestIsTheQuickestSingleLap() {
        assertThat(PracticeRanking.FASTEST.best(LAPS)).isEqualTo(13_900L);
    }

    @Test
    void bestTwoAndThreeAreTheQuickestRunsOfConsecutiveLaps() {
        assertThat(PracticeRanking.BEST_2.best(LAPS)).isEqualTo(28_500L);   // 14_000 + 14_500
        assertThat(PracticeRanking.BEST_3.best(LAPS)).isEqualTo(43_500L);   // 15_000 + 14_000 + 14_500
    }

    @Test
    void aDriverWithTooFewLapsHasNoRankedTime() {
        assertThat(PracticeRanking.BEST_3.best(List.of(14_000L, 14_500L))).isNull();
        assertThat(PracticeRanking.FASTEST.best(List.of())).isNull();
    }

    @Test
    void aLapWithNoTimeBreaksARun() {
        assertThat(PracticeRanking.BEST_2.best(Arrays.asList(14_000L, null, 14_500L))).isNull();
        assertThat(PracticeRanking.BEST_2.best(Arrays.asList(14_000L, null, 14_500L, 14_100L))).isEqualTo(28_600L);
    }
}
