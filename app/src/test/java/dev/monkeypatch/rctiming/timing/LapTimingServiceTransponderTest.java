package dev.monkeypatch.rctiming.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLogRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lap resolution with a primary and a secondary transponder per entry (L6, #14).
 * Calls {@link LapTimingService#onLapPassing} directly, so it runs synchronously.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LapTimingServiceTransponderTest {

    @Mock LiveTimingHub hub;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock EntryRepository entryRepository;
    @Mock CompetitorRepository competitorRepository;
    @Mock EntryAuditLogRepository auditLogRepository;
    @Mock EventRepository eventRepository;
    @Mock LapThresholdQuery lapThresholdQuery;

    private LapTimingService service;
    private final Map<Long, List<RaceEntry>> raceEntriesByRace = new HashMap<>();
    private final Map<Long, Entry> entriesById = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new LapTimingService(hub, raceEntryRepository, entryRepository, competitorRepository,
                lapThresholdQuery);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(anyLong()))
                .thenAnswer(inv -> raceEntriesByRace.getOrDefault(inv.<Long>getArgument(0), List.of()));
        when(entryRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(entriesById.get(inv.<Long>getArgument(0))));
        when(entryRepository.findAllById(any()))
                .thenAnswer(inv -> {
                    List<Entry> found = new ArrayList<>();
                    for (Long id : inv.<Iterable<Long>>getArgument(0)) {
                        if (entriesById.containsKey(id)) found.add(entriesById.get(id));
                    }
                    return found;
                });
    }

    @Test
    void aPassingWithNoRunningRaceIsIgnored() {
        lap(LapPassingEvent.NO_RACE, "1001", 1_000);

        assertThat(service.peek(LapPassingEvent.NO_RACE)).isEmpty();
        verifyNoInteractions(hub);
        verifyNoInteractions(raceEntryRepository);
    }

    @Test
    void aDatabaseFaultIsNotTreatedAsAnUnknownTransponder() {
        addEntry(1L, 10L, "1001", null, EntryStatus.CONFIRMED);
        doThrow(new IllegalStateException("database is locked")).when(entryRepository).findAllById(any());

        assertThatThrownBy(() -> lap(1L, "1001", 1_000)).isInstanceOf(IllegalStateException.class);

        verify(hub, never()).broadcastUnknownTransponder(anyLong(), eq("1001"));
    }

    @Test
    void secondaryTransponderGetsLaps() {
        addEntry(1L, 10L, "1001", "2002", EntryStatus.CONFIRMED);

        lap(1L, "1001", 1_000);
        lap(1L, "2002", 31_000);

        assertThat(lapsFor(1L, 10L)).isEqualTo(2);
        verify(hub, never()).broadcastUnknownTransponder(anyLong(), eq("2002"));
    }

    @Test
    void sameNumberInTwoDifferentEventsDoesNotConflict() {
        // Race 1 belongs to one event, race 2 to another; both entries use 5555
        addEntry(1L, 10L, "5555", null, EntryStatus.CONFIRMED);
        addEntry(2L, 20L, "5555", null, EntryStatus.CONFIRMED);

        lap(1L, "5555", 1_000);
        lap(2L, "5555", 2_000);

        assertThat(lapsFor(1L, 10L)).isEqualTo(1);
        assertThat(lapsFor(2L, 20L)).isEqualTo(1);
        verify(hub, never()).broadcastUnknownTransponder(anyLong(), eq("5555"));
    }

    @Test
    void sameNumberTwiceInOneRaceIsFlaggedAndNotCredited() {
        addEntry(1L, 10L, "7777", null, EntryStatus.CONFIRMED);
        addEntry(1L, 11L, "8888", "7777", EntryStatus.CONFIRMED);

        lap(1L, "7777", 1_000);
        lap(1L, "7777", 31_000);

        assertThat(lapsFor(1L, 10L)).isZero();
        assertThat(lapsFor(1L, 11L)).isZero();
        // Flagged once, on first sighting, so the referee can link it
        verify(hub).broadcastUnknownTransponder(1L, "7777");
    }

    @Test
    void refereeLinkResolvesAnAmbiguousNumber() {
        addEntry(1L, 10L, "7777", null, EntryStatus.CONFIRMED);
        addEntry(1L, 11L, "7777", null, EntryStatus.CONFIRMED);

        lap(1L, "7777", 1_000);
        service.linkTransponder(1L, "7777", 11L);
        lap(1L, "7777", 31_000);

        assertThat(lapsFor(1L, 11L)).isEqualTo(2);
        assertThat(lapsFor(1L, 10L)).isZero();
    }

    @Test
    void withdrawnEntryDoesNotMakeANumberAmbiguous() {
        addEntry(1L, 10L, "4444", null, EntryStatus.WITHDRAWN);
        addEntry(1L, 11L, "4444", null, EntryStatus.CONFIRMED);

        lap(1L, "4444", 1_000);

        assertThat(lapsFor(1L, 11L)).isEqualTo(1);
        verify(hub, never()).broadcastUnknownTransponder(anyLong(), eq("4444"));
    }

    @Test
    void swappedTransponderGetsLapsInTheNextRace() {
        // L11 acceptance: the swap goes through the real service, against the same repository
        // lap timing reads from
        addEntry(1L, 10L, "1001", null, EntryStatus.CONFIRMED);
        entriesById.get(10L).setEventId(99L);
        when(entryRepository.findByEventId(99L)).thenReturn(List.of(entriesById.get(10L)));
        when(entryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entriesById.get(10L)));
        when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(new Event()));
        lap(1L, "1001", 1_000);

        TransponderSwapService swapService =
                new TransponderSwapService(eventRepository, entryRepository, auditLogRepository, new ObjectMapper());
        SwapResult result = swapService.swap(99L, 10L, TransponderSlot.PRIMARY, "3003", 7L);
        assertThat(result).isInstanceOf(SwapResult.Success.class);

        RaceEntry nextRace = new RaceEntry();
        nextRace.setRaceId(2L);
        nextRace.setEntryId(10L);
        raceEntriesByRace.computeIfAbsent(2L, k -> new ArrayList<>()).add(nextRace);

        lap(2L, "3003", 1_000);
        lap(2L, "3003", 31_000);
        lap(2L, "1001", 32_000);

        assertThat(lapsFor(2L, 10L)).isEqualTo(2);
        verify(hub).broadcastUnknownTransponder(2L, "1001");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void addEntry(long raceId, long entryId, String primary, String secondary, EntryStatus status) {
        Entry entry = new Entry();
        entry.setId(entryId);
        entry.setTransponderNumberSnapshot(primary);
        entry.setSecondaryTransponderNumber(secondary);
        entry.setStatus(status);
        entriesById.put(entryId, entry);

        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setRaceId(raceId);
        raceEntry.setEntryId(entryId);
        raceEntriesByRace.computeIfAbsent(raceId, k -> new ArrayList<>()).add(raceEntry);
    }

    private void lap(long raceId, String transponder, long millis) {
        service.onLapPassing(new LapPassingEvent(raceId, transponder, millis * 1000L));
    }

    private int lapsFor(long raceId, long entryId) {
        return service.peek(raceId).orElseThrow().calculatePositions().stream()
                .filter(r -> r.entryId() == entryId)
                .mapToInt(LiveTimingRowDto::lapsCompleted)
                .findFirst()
                .orElse(0);
    }
}
