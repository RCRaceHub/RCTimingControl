package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A timed race ends at the flag (TRACK-05): the clock running out, and the finish once every car has taken it. */
class LapTimingServiceFlagTest {

    private static final long RACE = 1L;

    private RaceClockService clock;
    private ApplicationEventPublisher publisher;
    private LapTimingService service;
    private long elapsedMs;

    @BeforeEach
    void setUp() {
        RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
        EntryRepository entries = mock(EntryRepository.class);
        clock = mock(RaceClockService.class);
        publisher = mock(ApplicationEventPublisher.class);
        LapThresholdQuery thresholds = mock(LapThresholdQuery.class);
        service = new LapTimingService(mock(LiveTimingHub.class), raceEntries, entries,
                mock(CompetitorRepository.class), thresholds, clock, publisher);

        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setRaceId(RACE);
        raceEntry.setEntryId(10L);
        when(raceEntries.findByRaceIdOrderByGridPosition(RACE)).thenReturn(List.of(raceEntry));
        Entry entry = new Entry();
        entry.setId(10L);
        entry.setTransponderNumberSnapshot("1001");
        entry.setStatus(EntryStatus.CONFIRMED);
        when(entries.findAllById(any())).thenReturn(List.of(entry));
        when(entries.findById(anyLong())).thenReturn(Optional.of(entry));

        when(clock.planOf(RACE)).thenReturn(Optional.of(new RaceClockService.RacePlan(300_000L, 0)));
        when(clock.clockOf(anyLong(), any(RaceStatus.class), any())).thenAnswer(inv -> new RaceClockDto(RACE,
                "RUNNING", elapsedMs, 300_000L, Math.max(0, 300_000L - elapsedMs), true));
    }

    private void lap(long seconds) {
        service.onLapPassing(new LapPassingEvent(RACE, "1001", seconds * 1_000_000L));
    }

    @Test
    void theRaceIsOverWhenTheOnlyCarCrossesAfterTheClockRanOut() {
        elapsedMs = 240_000;
        lap(10);
        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));

        elapsedMs = 300_000;
        lap(40);
        verify(publisher).publishEvent(new AllCarsFlaggedEvent(RACE));
    }

    @Test
    void theFinishIsAskedForOnceAndLaterPassingsAreNotCounted() {
        elapsedMs = 300_000;
        lap(10);
        lap(40);
        lap(70);

        verify(publisher, times(1)).publishEvent(any(AllCarsFlaggedEvent.class));
        assertThat(service.peek(RACE).orElseThrow().getPositionSnapshot(10L).getLapsCompleted()).isEqualTo(1);
    }

    @Test
    void theRaceLengthIsReadOnceNotOnEveryPassing() {
        elapsedMs = 240_000;
        lap(10);
        lap(40);
        lap(70);

        verify(clock, times(1)).planOf(RACE);
    }

    @Test
    void aRaceWithNoLengthNeverReachesTheFlag() {
        when(clock.planOf(RACE)).thenReturn(Optional.of(RaceClockService.RacePlan.NONE));
        elapsedMs = 300_000;
        lap(10);
        lap(40);

        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));
    }

    /** A second car, in grid order after the first, in a stagger start with the given gap. */
    private void staggerWithSecondCar(long intervalMs) {
        RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
        EntryRepository entries = mock(EntryRepository.class);
        service = new LapTimingService(mock(LiveTimingHub.class), raceEntries, entries,
                mock(CompetitorRepository.class), mock(LapThresholdQuery.class), clock, publisher);
        List<RaceEntry> grid = new java.util.ArrayList<>();
        List<Entry> found = new java.util.ArrayList<>();
        for (long id : new long[]{10L, 11L}) {
            RaceEntry raceEntry = new RaceEntry();
            raceEntry.setRaceId(RACE);
            raceEntry.setEntryId(id);
            grid.add(raceEntry);
            Entry entry = new Entry();
            entry.setId(id);
            entry.setTransponderNumberSnapshot(String.valueOf(1000 + id));
            entry.setStatus(EntryStatus.CONFIRMED);
            found.add(entry);
        }
        when(raceEntries.findByRaceIdOrderByGridPosition(RACE)).thenReturn(grid);
        when(entries.findAllById(any())).thenReturn(found);
        when(entries.findById(anyLong())).thenAnswer(inv -> found.stream()
                .filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        when(clock.planOf(RACE)).thenReturn(Optional.of(new RaceClockService.RacePlan(300_000L, intervalMs)));
    }

    private void lapOf(String transponder, long seconds) {
        service.onLapPassing(new LapPassingEvent(RACE, transponder, seconds * 1_000_000L));
    }

    @Test
    void aStaggeredCarKeepsRacingUntilItsOwnWindowEnds() {
        staggerWithSecondCar(5_000);
        elapsedMs = 100_000;
        lapOf("1010", 10);
        lapOf("1011", 12);

        // The race clock is up for the first car, but the second left 5 s later and has 5 s to go
        elapsedMs = 302_000;
        lapOf("1010", 40);
        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));
        lapOf("1011", 42);
        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));

        elapsedMs = 306_000;
        lapOf("1011", 70);
        verify(publisher).publishEvent(new AllCarsFlaggedEvent(RACE));
    }

    @Test
    void theRaceIsNotOverWhileTheLastCarToStartHasNotCrossed() {
        staggerWithSecondCar(120_000);
        // Car 10's window is up, car 11 left 2 minutes later and has not crossed the line yet
        elapsedMs = 100_000;
        lapOf("1010", 10);
        elapsedMs = 305_000;
        lapOf("1010", 40);

        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));
    }
}
