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
    private long remainingMs;

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

        when(clock.durationMsOf(RACE)).thenReturn(Optional.of(300_000L));
        when(clock.clockOf(anyLong(), any(RaceStatus.class), any()))
                .thenAnswer(inv -> new RaceClockDto(RACE, "RUNNING", 0, 300_000L, remainingMs, true));
    }

    private void lap(long seconds) {
        service.onLapPassing(new LapPassingEvent(RACE, "1001", seconds * 1_000_000L));
    }

    @Test
    void theRaceIsOverWhenTheOnlyCarCrossesAfterTheClockRanOut() {
        remainingMs = 60_000;
        lap(10);
        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));

        remainingMs = 0;
        lap(40);
        verify(publisher).publishEvent(new AllCarsFlaggedEvent(RACE));
    }

    @Test
    void theFinishIsAskedForOnceAndLaterPassingsAreNotCounted() {
        remainingMs = 0;
        lap(10);
        lap(40);
        lap(70);

        verify(publisher, times(1)).publishEvent(any(AllCarsFlaggedEvent.class));
        assertThat(service.peek(RACE).orElseThrow().getPositionSnapshot(10L).getLapsCompleted()).isEqualTo(1);
    }

    @Test
    void theRaceLengthIsReadOnceNotOnEveryPassing() {
        remainingMs = 60_000;
        lap(10);
        lap(40);
        lap(70);

        verify(clock, times(1)).durationMsOf(RACE);
    }

    @Test
    void aRaceWithNoLengthNeverReachesTheFlag() {
        when(clock.durationMsOf(RACE)).thenReturn(Optional.empty());
        remainingMs = 0;
        lap(10);
        lap(40);

        verify(publisher, never()).publishEvent(any(AllCarsFlaggedEvent.class));
    }
}
