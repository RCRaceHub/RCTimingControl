package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.service.QualifyingStandingsService.QualifyingResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QualifyingStandingsServiceTest {

    @Test
    void rank_putsMostLapsFirstThenTheQuickestBestLap() {
        List<Long> order = QualifyingStandingsService.rank(List.of(
                new QualifyingResult(1L, 50_000, 20),
                new QualifyingResult(2L, 48_000, 20),
                new QualifyingResult(3L, 60_000, 21),
                new QualifyingResult(4L, 40_000, 19)));

        assertThat(order).containsExactly(3L, 2L, 1L, 4L);
    }

    @Test
    void rank_putsADriverWithNoTimedLapBehindOthersOnTheSameLaps() {
        List<Long> order = QualifyingStandingsService.rank(List.of(
                new QualifyingResult(1L, Long.MAX_VALUE, 5),
                new QualifyingResult(2L, 70_000, 5)));

        assertThat(order).containsExactly(2L, 1L);
    }

    @Test
    void plus_addsLapsAndKeepsTheQuickerBestLap() {
        QualifyingResult total = new QualifyingResult(7L, 55_000, 10).plus(new QualifyingResult(7L, 52_000, 11));

        assertThat(total).isEqualTo(new QualifyingResult(7L, 52_000, 21));
        assertThat(total.points()).isNull();
    }

    @Test
    void rank_breaksAnExactTieByEntryIdSoTheOrderNeverChanges() {
        List<Long> order = QualifyingStandingsService.rank(List.of(
                new QualifyingResult(9L, 50_000, 20),
                new QualifyingResult(3L, 50_000, 20),
                new QualifyingResult(5L, 50_000, 20)));

        assertThat(order).containsExactly(3L, 5L, 9L);
    }

    private static QualifyingResult total(long entryId, int points, int laps) {
        return new QualifyingResult(entryId, 50_000, laps, points, List.of());
    }

    private static List<Long> brcaRank(Map<Long, QualifyingStandingsService.CountingRounds> counting,
                                       QualifyingResult... results) {
        List<QualifyingResult> sorted = new ArrayList<>(List.of(results));
        sorted.sort(QualifyingStandingsService.brcaOrder(counting));
        return sorted.stream().map(QualifyingResult::entryId).toList();
    }

    @Test
    void brcaOrder_putsTheBestSingleRoundAheadOfTwoSecondPlaces() {
        // 1 + 3 and 2 + 2 are both 4 points; the driver with the single best round wins even with fewer laps
        var counting = Map.of(
                1L, new QualifyingStandingsService.CountingRounds(List.of(2, 2), 600_000),
                2L, new QualifyingStandingsService.CountingRounds(List.of(1, 3), 600_000));

        assertThat(brcaRank(counting, total(1, 4, 40), total(2, 4, 30))).containsExactly(2L, 1L);
    }

    @Test
    void brcaOrder_comparesTheSecondBestRoundWhenTheBestIsTheSame() {
        var counting = Map.of(
                1L, new QualifyingStandingsService.CountingRounds(List.of(1, 5), 600_000),
                2L, new QualifyingStandingsService.CountingRounds(List.of(1, 4), 600_000));

        assertThat(brcaRank(counting, total(1, 6, 40), total(2, 5, 30))).containsExactly(2L, 1L);
        assertThat(brcaRank(counting, total(1, 5, 40), total(2, 5, 30))).containsExactly(2L, 1L);
    }

    @Test
    void brcaOrder_fallsBackToCountingLapsThenCountingTime() {
        var counting = Map.of(
                1L, new QualifyingStandingsService.CountingRounds(List.of(1, 3), 610_000),
                2L, new QualifyingStandingsService.CountingRounds(List.of(1, 3), 600_000),
                3L, new QualifyingStandingsService.CountingRounds(List.of(1, 3), 620_000));

        // Same rounds: more laps first, then the shorter combined time
        assertThat(brcaRank(counting, total(1, 4, 40), total(2, 4, 40), total(3, 4, 41)))
                .containsExactly(3L, 2L, 1L);
    }
}
