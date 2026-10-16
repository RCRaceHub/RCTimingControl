package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.service.QualifyingStandingsService.QualifyingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
