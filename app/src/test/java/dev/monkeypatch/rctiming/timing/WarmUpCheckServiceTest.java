package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class WarmUpCheckServiceTest {

    private static final long RACE = 7L;

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private final LiveTimingHub hub = mock(LiveTimingHub.class);
    private final WarmUpCheckService service =
            new WarmUpCheckService(raceRepository, raceEntryRepository, entryRepository, hub);

    @BeforeEach
    void setUp() {
        Race grid = new Race();
        grid.setId(RACE);
        grid.setStatus(RaceStatus.GRID);
        when(raceRepository.findFirstByStatus(RaceStatus.GRID)).thenReturn(Optional.of(grid));
        RaceEntry first = raceEntry(1L);
        RaceEntry second = raceEntry(2L);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(RACE)).thenReturn(List.of(first, second));
        when(entryRepository.findAllById(any())).thenReturn(List.of(
                entry(1L, "1001", null, EntryStatus.CONFIRMED),
                entry(2L, "2001", "2002", EntryStatus.CONFIRMED)));
    }

    @Test
    void aPassingOnTheGridMarksTheCarSeenAndBroadcasts() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        assertThat(service.seenEntryIds(RACE)).containsExactly(1L);
        verify(hub).broadcastWarmUp(RACE, List.of(1L));
    }

    @Test
    void theSecondaryNumberCountsToo() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "2002", 1L));

        assertThat(service.seenEntryIds(RACE)).containsExactly(2L);
    }

    @Test
    void aCarSeenAgainIsNotBroadcastAgain() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 2L));

        verify(hub, times(1)).broadcastWarmUp(anyLong(), any());
    }

    @Test
    void aNumberInNoEntryIsIgnored() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "9999", 1L));

        assertThat(service.seenEntryIds(RACE)).isEmpty();
        verify(hub, never()).broadcastWarmUp(anyLong(), any());
    }

    @Test
    void aPassingInARunningRaceIsNotAWarmUp() {
        service.onLapPassing(new LapPassingEvent(RACE, "1001", 1L));

        assertThat(service.seenEntryIds(RACE)).isEmpty();
    }

    @Test
    void nothingIsKeptWhenNoRaceIsOnTheGrid() {
        when(raceRepository.findFirstByStatus(RaceStatus.GRID)).thenReturn(Optional.empty());

        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        verify(hub, never()).broadcastWarmUp(anyLong(), any());
    }

    @Test
    void aWithdrawnEntryIsNotMarkedSeen() {
        when(entryRepository.findAllById(any())).thenReturn(List.of(entry(1L, "1001", null, EntryStatus.WITHDRAWN)));

        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        assertThat(service.seenEntryIds(RACE)).isEmpty();
    }

    @Test
    void theRaceStartingForgetsWhoWasSeen() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE, RaceStatus.RUNNING));

        assertThat(service.seenEntryIds(RACE)).isEmpty();
    }

    @Test
    void leavingTheGridSendsAnEmptyListSoScreensDropTheirMarks() {
        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE, RaceStatus.PENDING));

        verify(hub).broadcastWarmUp(RACE, List.of());
    }

    @Test
    void aPassingForARaceThatHasLeftTheGridIsNotMarked() {
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE, RaceStatus.RUNNING));

        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        assertThat(service.seenEntryIds(RACE)).isEmpty();
    }

    @Test
    void callingTheGridAgainReopensTheCheck() {
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE, RaceStatus.PENDING));
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE, RaceStatus.GRID));

        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        assertThat(service.seenEntryIds(RACE)).containsExactly(1L);
    }

    @Test
    void aFaultIsSwallowedSoPracticeStillGetsThePassing() {
        when(raceRepository.findFirstByStatus(RaceStatus.GRID)).thenThrow(new IllegalStateException("database"));

        service.onLapPassing(new LapPassingEvent(LapPassingEvent.NO_RACE, "1001", 1L));

        verify(hub, never()).broadcastWarmUp(anyLong(), any());
    }

    private static RaceEntry raceEntry(long entryId) {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setEntryId(entryId);
        return raceEntry;
    }

    private static Entry entry(long id, String primary, String secondary, EntryStatus status) {
        Entry entry = new Entry();
        entry.setId(id);
        entry.setTransponderNumberSnapshot(primary);
        entry.setSecondaryTransponderNumber(secondary);
        entry.setStatus(status);
        return entry;
    }
}
