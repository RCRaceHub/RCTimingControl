package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeLap;
import dev.monkeypatch.rctiming.domain.practice.PracticeLapRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PracticeTimingService.
 * Uses Mockito mocks for all repositories and the hub.
 * No Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class PracticeTimingServiceTest {

    @Mock
    PracticeSessionRepository sessionRepository;
    @Mock
    PracticeLapRepository lapRepository;
    @Mock
    PracticeDriverRepository driverRepository;
    @Mock
    EntryRepository entryRepository;
    @Mock
    CompetitorRepository competitorRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    PracticeTimingHub timingHub;

    PracticeTimingService service;
    PracticeSession runningSession;

    @BeforeEach
    void setUp() {
        service = new PracticeTimingService(
                sessionRepository, lapRepository, driverRepository,
                entryRepository, competitorRepository, userRepository, timingHub);

        runningSession = new PracticeSession();
        runningSession.setName("Test Session");
        // Use reflection to set the id the repository would set on save
        try {
            var idField = PracticeSession.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(runningSession, 42L);
            var statusField = PracticeSession.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(runningSession, PracticeStatus.RUNNING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void onLapPassingEvent_runningSession_recordsLap() {
        // First passing (no previous RTC — no lap time)
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));

        // Second passing — lap time calculated
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_060_000_000L)); // 60s gap

        // A lap record should have been persisted for the second passing
        verify(lapRepository, times(1)).save(any(PracticeLap.class));
    }

    @Test
    void lapsAreNumberedInMemoryAfterTheFirstLookup() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        // Laps 1 and 3 were saved before a restart in the middle of the session: the numbers have a gap
        PracticeLap first = new PracticeLap();
        first.setLapNumber(1);
        PracticeLap third = new PracticeLap();
        third.setLapNumber(3);
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(42L, "T1"))
                .thenReturn(List.of(first, third));

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_060_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_120_000_000L));

        ArgumentCaptor<PracticeLap> saved = ArgumentCaptor.forClass(PracticeLap.class);
        verify(lapRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PracticeLap::getLapNumber).containsExactly(4, 5);
        // Looked up once for the transponder, not once per passing
        verify(lapRepository, times(1))
                .findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString());
    }

    @Test
    void aLongPauseStartsANewRunAndIsNotSavedAsALap() {
        runningSession.setPauseSeconds(120);
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_030_000_000L)); // 30s: a lap of run 1
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_600_000_000L)); // 570s pause: starts run 2, no lap
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_630_000_000L)); // 30s: a lap of run 2

        ArgumentCaptor<PracticeLap> saved = ArgumentCaptor.forClass(PracticeLap.class);
        verify(lapRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PracticeLap::getRunNumber).containsExactly(1, 2);
        assertThat(saved.getAllValues()).extracting(PracticeLap::getLapTimeMs).containsExactly(30_000L, 30_000L);
    }

    @Test
    void aTransponderNamedInTheSessionShowsThatName() {
        PracticeDriver named = new PracticeDriver();
        named.setCompetitorId(300L);
        Competitor grace = new Competitor();
        grace.setDisplayName("Grace Hopper");
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(driverRepository.findByPracticeSessionIdAndTransponderNumber(42L, "T5")).thenReturn(Optional.of(named));
        when(competitorRepository.findById(300L)).thenReturn(Optional.of(grace));

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T5", 1_000_000_000L));

        PracticeTimingRowDto row = service.getSnapshot(42L).get(0);
        assertThat(row.racerName()).isEqualTo("Grace Hopper");
        assertThat(row.isUnknown()).isFalse();
    }

    @Test
    void aPassingNoLaterThanThePreviousGivesNoLapTime() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));

        verify(lapRepository, never()).save(any());
    }

    @Test
    void onLapPassingEvent_duringARace_isIgnoredEvenWithAPracticeSessionRunning() {
        // A passing with a race id is the race's; a practice session left running must not count it too
        service.onLapPassing(new LapPassingEvent(7L, "T1", 1_000_000_000L));

        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(lapRepository);
    }

    @Test
    void onLapPassingEvent_noSession_ignored() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.empty());

        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));

        verify(lapRepository, never()).save(any());
        verifyNoInteractions(timingHub);
    }

    @Test
    void onLapPassingEvent_unknownTransponder_recordsWithNullUser() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        service.startSession(runningSession);
        // Two passings to generate a lap time
        service.onLapPassing(new LapPassingEvent(0L, "UNKNOWN", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "UNKNOWN", 1_060_000_000L));

        // Lap should be saved with null user
        ArgumentCaptor<PracticeLap> captor = ArgumentCaptor.forClass(PracticeLap.class);
        verify(lapRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getTransponderNumber()).isEqualTo("UNKNOWN");
    }

    @Test
    void onLapPassingEvent_sessionForAnEvent_namesTheCompetitorWhoseEntryUsesTheTransponder() {
        runningSession.setEventId(7L);
        Entry withdrawn = entry(1L, 100L, "T9", null, EntryStatus.WITHDRAWN);
        Entry active = entry(2L, 200L, "P1", "T9", EntryStatus.CONFIRMED);
        Competitor ada = new Competitor();
        ada.setDisplayName("Ada Lovelace");
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(entryRepository.findByEventId(7L)).thenReturn(List.of(withdrawn, active));
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(ada));

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T9", 1_000_000_000L));

        PracticeTimingRowDto row = service.getSnapshot(42L).get(0);
        assertThat(row.racerName()).isEqualTo("Ada Lovelace");
        assertThat(row.isUnknown()).isFalse();
    }

    @Test
    void getSnapshot_afterStop_stillNamesTheCompetitorFromTheEventEntries() {
        runningSession.setEventId(7L);
        PracticeLap lap = new PracticeLap();
        lap.setPracticeSessionId(runningSession.getId());
        lap.setTransponderNumber("T9");
        lap.setLapNumber(1);
        lap.setLapTimeMs(60_000L);
        lap.setCrossingTime(Instant.now());
        Competitor ada = new Competitor();
        ada.setDisplayName("Ada Lovelace");
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdOrderByCrossingTimeAsc(42L)).thenReturn(List.of(lap));
        when(entryRepository.findByEventId(7L)).thenReturn(List.of(entry(2L, 200L, "T9", null, EntryStatus.CONFIRMED)));
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(ada));

        service.stopSession(42L);
        PracticeTimingRowDto row = service.getSnapshot(42L).get(0);

        assertThat(row.racerName()).isEqualTo("Ada Lovelace");
        assertThat(row.isUnknown()).isFalse();
    }

    @Test
    void getSnapshot_returnsCurrentPositions() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        service.startSession(runningSession);
        // Record 2 passings to get 1 lap time
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_060_000_000L));

        List<PracticeTimingRowDto> snapshot = service.getSnapshot(42L);

        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).transponderNumber()).isEqualTo("T1");
        assertThat(snapshot.get(0).laps()).isEqualTo(1);
        assertThat(snapshot.get(0).isUnknown()).isTrue();
    }

    @Test
    void broadcastsViaStompAfterEachPassing() {
        when(sessionRepository.findRunningSession()).thenReturn(Optional.of(runningSession));
        when(lapRepository.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(anyLong(), anyString()))
                .thenReturn(Collections.emptyList());

        service.startSession(runningSession);
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_000_000_000L));
        service.onLapPassing(new LapPassingEvent(0L, "T1", 1_060_000_000L));

        // broadcastTimingUpdate called once per passing = 2 times total
        verify(timingHub, times(2)).broadcastTimingUpdate(eq(42L), any());
    }

    private static Entry entry(Long id, Long competitorId, String primary, String secondary, EntryStatus status) {
        Entry e = new Entry();
        setField(Entry.class, e, "id", id);
        e.setCompetitorId(competitorId);
        e.setTransponderNumberSnapshot(primary);
        e.setSecondaryTransponderNumber(secondary);
        e.setStatus(status);
        return e;
    }

    private static <T> void setField(Class<T> type, T target, String name, Object value) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
