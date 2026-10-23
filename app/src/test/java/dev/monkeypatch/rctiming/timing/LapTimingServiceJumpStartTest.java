package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A car that crosses the line before its turn in a stagger start is reported to the referee. */
class LapTimingServiceJumpStartTest {

    private static final long RACE = 1L;
    private static final long INTERVAL_MS = 10_000;

    private LiveTimingHub hub;
    private LapTimingService service;
    private long elapsedMs;

    @BeforeEach
    void setUp() {
        hub = mock(LiveTimingHub.class);
        RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
        EntryRepository entries = mock(EntryRepository.class);
        RaceClockService clock = mock(RaceClockService.class);
        service = new LapTimingService(hub, raceEntries, entries, mock(CompetitorRepository.class),
                mock(LapThresholdQuery.class), clock, mock(ApplicationEventPublisher.class));

        // Entry 10 is called first (offset 0), entry 20 second (offset 10 s)
        when(raceEntries.findByRaceIdOrderByGridPosition(RACE)).thenReturn(List.of(raceEntry(10L, 1), raceEntry(20L, 2)));
        when(entries.findById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            Entry entry = new Entry();
            entry.setId(id);
            entry.setTransponderNumberSnapshot(String.valueOf(id * 100));
            entry.setStatus(EntryStatus.CONFIRMED);
            return Optional.of(entry);
        });
        Entry first = new Entry();
        first.setId(10L);
        first.setTransponderNumberSnapshot("1000");
        first.setStatus(EntryStatus.CONFIRMED);
        Entry second = new Entry();
        second.setId(20L);
        second.setTransponderNumberSnapshot("2000");
        second.setStatus(EntryStatus.CONFIRMED);
        when(entries.findAllById(any())).thenReturn(List.of(first, second));

        when(clock.planOf(RACE)).thenReturn(Optional.of(new RaceClockService.RacePlan(300_000L, INTERVAL_MS)));
        when(clock.clockOf(anyLong(), any(RaceStatus.class), any())).thenAnswer(inv -> new RaceClockDto(RACE,
                "RUNNING", elapsedMs, 300_000L, 300_000L - elapsedMs, true));
    }

    private static RaceEntry raceEntry(long entryId, int gridPosition) {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setRaceId(RACE);
        raceEntry.setEntryId(entryId);
        raceEntry.setGridPosition(gridPosition);
        return raceEntry;
    }

    private void pass(String transponder, long seconds) {
        service.onLapPassing(new LapPassingEvent(RACE, transponder, seconds * 1_000_000L));
    }

    @Test
    void aCarCrossingBeforeItsTurnIsReportedOnce() {
        elapsedMs = 4_000;
        pass("2000", 100);
        elapsedMs = 5_000;
        pass("2000", 101);

        List<JumpStartDto> jumpStarts = service.jumpStartsOf(RACE);
        assertThat(jumpStarts).hasSize(1);
        assertThat(jumpStarts.get(0).entryId()).isEqualTo(20L);
        assertThat(jumpStarts.get(0).earlyMs()).isEqualTo(6_000);
        verify(hub, times(1)).broadcastJumpStarts(RACE, jumpStarts);
    }

    @Test
    void takingNoActionKeepsTheCarListedButMarksItDismissed() {
        elapsedMs = 4_000;
        pass("2000", 100);

        assertThat(service.dismissJumpStart(RACE, 20L)).isPresent();

        List<JumpStartDto> jumpStarts = service.jumpStartsOf(RACE);
        assertThat(jumpStarts).hasSize(1);
        assertThat(jumpStarts.get(0).dismissed()).isTrue();
        verify(hub, times(2)).broadcastJumpStarts(eq(RACE), any());
    }

    @Test
    void aCarThatDidNotJumpHasNothingToDismiss() {
        elapsedMs = 12_000;
        pass("2000", 100);

        assertThat(service.dismissJumpStart(RACE, 20L)).isEmpty();
        assertThat(service.dismissJumpStart(RACE, 99L)).isEmpty();
    }

    @Test
    void releasingTheRaceClearsTheAlertsOnOpenScreens() {
        elapsedMs = 4_000;
        pass("2000", 100);

        service.releaseState(RACE);

        assertThat(service.jumpStartsOf(RACE)).isEmpty();
        verify(hub).broadcastJumpStarts(RACE, List.of());
    }

    @Test
    void aCarCrossingAfterItsTurnIsNotReported() {
        elapsedMs = 12_000;
        pass("2000", 100);
        elapsedMs = 100;
        pass("1000", 101);

        assertThat(service.jumpStartsOf(RACE)).isEmpty();
        verify(hub, never()).broadcastJumpStarts(anyLong(), any());
    }

    @Test
    void aCarWithinTheGraceOfItsTurnIsNotReported() {
        elapsedMs = INTERVAL_MS - LapTimingService.JUMP_START_GRACE_MS;
        pass("2000", 100);

        assertThat(service.jumpStartsOf(RACE)).isEmpty();
    }

    @Test
    void aRaceThatIsNotStaggeredNeverReportsAJumpStart() {
        RaceClockService clock = mock(RaceClockService.class);
        when(clock.planOf(RACE)).thenReturn(Optional.of(new RaceClockService.RacePlan(300_000L, 0)));
        when(clock.clockOf(anyLong(), any(RaceStatus.class), any())).thenAnswer(inv -> new RaceClockDto(RACE,
                "RUNNING", 0, 300_000L, 300_000L, true));
        RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
        EntryRepository entries = mock(EntryRepository.class);
        Entry entry = new Entry();
        entry.setId(20L);
        entry.setTransponderNumberSnapshot("2000");
        entry.setStatus(EntryStatus.CONFIRMED);
        when(raceEntries.findByRaceIdOrderByGridPosition(RACE)).thenReturn(List.of(raceEntry(20L, 2)));
        when(entries.findAllById(any())).thenReturn(List.of(entry));
        when(entries.findById(anyLong())).thenReturn(Optional.of(entry));
        LapTimingService together = new LapTimingService(hub, raceEntries, entries, mock(CompetitorRepository.class),
                mock(LapThresholdQuery.class), clock, mock(ApplicationEventPublisher.class));

        together.onLapPassing(new LapPassingEvent(RACE, "2000", 100_000_000L));

        assertThat(together.jumpStartsOf(RACE)).isEmpty();
        verify(hub, never()).broadcastJumpStarts(anyLong(), any());
    }
}
