package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.practice.PracticeDayAggregator.Driver;
import dev.monkeypatch.rctiming.practice.PracticeDayAggregator.Lap;
import dev.monkeypatch.rctiming.practice.dto.PracticeDayRowDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The day view adds a driver's laps up across sessions (#151). */
class PracticeDayAggregatorTest {

    private static final Driver ALEX = new Driver(7L, "Alex");

    private static PracticeDayAggregator.DriverLookup alexOnT1AndT2() {
        return (session, transponder) -> transponder.equals("T3") ? new Driver(null, null) : ALEX;
    }

    @Test
    void oneTransponderInTwoSessionsIsOneDriver() {
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T1", 1, 30_000), new Lap(1, "T1", 1, 29_000),
                new Lap(2, "T1", 1, 31_000)), alexOnT1AndT2(), 2);

        assertThat(rows).hasSize(1);
        PracticeDayRowDto row = rows.get(0);
        assertThat(row.name()).isEqualTo("Alex");
        assertThat(row.sessions()).isEqualTo(2);
        assertThat(row.laps()).isEqualTo(3);
        assertThat(row.runs()).isEqualTo(2);
        assertThat(row.bestLapMs()).isEqualTo(29_000);
    }

    @Test
    void aCompetitorOnTwoTransponderNumbersIsOneRow() {
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T1", 1, 30_000), new Lap(2, "T2", 1, 28_000)), alexOnT1AndT2(), 1);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).transponderNumbers()).isEqualTo("T1, T2");
    }

    @Test
    void theBestAverageStaysWithinOneRun() {
        // Two runs of one lap each: no run holds 2 laps, so there is no average, though 4 laps were driven
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T1", 1, 30_000), new Lap(1, "T1", 2, 30_000),
                new Lap(2, "T1", 1, 30_000), new Lap(2, "T1", 2, 30_000)), alexOnT1AndT2(), 2);

        assertThat(rows.get(0).bestConsecutiveNMs()).isNull();
    }

    @Test
    void theBestAverageIsTheQuickestWindowOfAnyRun() {
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T1", 1, 40_000), new Lap(1, "T1", 1, 30_000), new Lap(1, "T1", 1, 31_000),
                new Lap(2, "T1", 1, 29_000), new Lap(2, "T1", 1, 29_500)), alexOnT1AndT2(), 2);

        assertThat(rows.get(0).bestConsecutiveNMs()).isEqualTo(58_500);
    }

    @Test
    void anUnnamedTransponderIsItsOwnRowAndRowsRunQuickestFirst() {
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T3", 1, 20_000), new Lap(1, "T3", 1, 20_000),
                new Lap(1, "T1", 1, 30_000), new Lap(1, "T1", 1, 30_000)), alexOnT1AndT2(), 2);

        assertThat(rows).extracting(PracticeDayRowDto::name).containsExactly(null, "Alex");
        assertThat(rows.get(0).transponderNumbers()).isEqualTo("T3");
    }

    @Test
    void driversWithNoAverageComeLast() {
        List<PracticeDayRowDto> rows = PracticeDayAggregator.aggregate(List.of(
                new Lap(1, "T3", 1, 10_000),
                new Lap(1, "T1", 1, 30_000), new Lap(1, "T1", 1, 30_000)), alexOnT1AndT2(), 2);

        assertThat(rows).extracting(PracticeDayRowDto::name).containsExactly("Alex", null);
    }
}
