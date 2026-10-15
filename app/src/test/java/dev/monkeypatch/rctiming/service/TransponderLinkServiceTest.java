package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAudit;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransponderLinkServiceTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock LapTimingService lapTimingService;
    @Mock UnknownTransponderLinkAuditRepository linkAuditRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) AuditService audit;
    @Mock RaceAuditLabels labels;
    @Mock EntryRepository entryRepository;
    @Mock TransponderSwapService swapService;

    private TransponderLinkService service() {
        return new TransponderLinkService(raceRepository, raceEntryRepository, lapTimingService, linkAuditRepository,
                audit, labels, entryRepository, swapService);
    }

    @Test
    void link_recordsItThenCreditsThePassingsCountedBeforehand() {
        runningRaceWithEntry11(new LiveRaceState());
        when(lapTimingService.countPassingsForTransponder(7L, "1234567")).thenReturn(4);

        int lapsCredited = service().link(7L, "1234567", 11L, 3L);

        assertThat(lapsCredited).isEqualTo(3);
        ArgumentCaptor<UnknownTransponderLinkAudit> row = ArgumentCaptor.forClass(UnknownTransponderLinkAudit.class);
        InOrder order = inOrder(lapTimingService, linkAuditRepository);
        order.verify(lapTimingService).countPassingsForTransponder(7L, "1234567");
        order.verify(linkAuditRepository).save(row.capture());
        order.verify(lapTimingService).linkTransponder(7L, "1234567", 11L);
        assertThat(row.getValue().getRaceId()).isEqualTo(7L);
        assertThat(row.getValue().getTransponderNumber()).isEqualTo("1234567");
        assertThat(row.getValue().getEntryId()).isEqualTo(11L);
        assertThat(row.getValue().getLinkedByUserId()).isEqualTo(3L);
        verify(audit).entry(Actor.official(3L), "UNKNOWN_TRANSPONDER_LINKED");
    }

    @Test
    void link_whenRecordingFails_doesNotLink() {
        runningRaceWithEntry11(new LiveRaceState());
        when(linkAuditRepository.save(any())).thenThrow(new IllegalStateException("disk full"));

        assertThatThrownBy(() -> service().link(7L, "1234567", 11L, 3L)).isInstanceOf(IllegalStateException.class);

        verify(lapTimingService, never()).linkTransponder(anyLong(), any(), anyLong());
    }

    @Test
    void link_inATransaction_changesTheLiveTimingOnlyOnceItCommits() {
        runningRaceWithEntry11(new LiveRaceState());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service().link(7L, "1234567", 11L, 3L);
            verify(lapTimingService, never()).linkTransponder(anyLong(), any(), anyLong());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(lapTimingService).linkTransponder(7L, "1234567", 11L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void link_inATransactionThatRollsBack_neverChangesTheLiveTiming() {
        runningRaceWithEntry11(new LiveRaceState());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service().link(7L, "1234567", 11L, 3L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(lapTimingService, never()).linkTransponder(anyLong(), any(), anyLong());
    }

    @Test
    void link_inAFinishedRace_isRefused() {
        when(raceRepository.getOrThrow(7L)).thenReturn(race(RaceStatus.FINISHED));

        assertThatThrownBy(() -> service().link(7L, "1234567", 11L, 3L))
                .isInstanceOf(StateConflictException.class);

        verifyNoInteractions(linkAuditRepository, lapTimingService);
    }

    @Test
    void link_toAnEntryNotInTheRace_isRefused() {
        when(raceRepository.getOrThrow(7L)).thenReturn(race(RaceStatus.RUNNING));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(List.of(raceEntry(12L)));

        assertThatThrownBy(() -> service().link(7L, "1234567", 11L, 3L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(linkAuditRepository, lapTimingService);
    }

    @Test
    void link_again_toTheSameEntry_creditsAndRecordsNothing() {
        LiveRaceState state = new LiveRaceState();
        state.retroactiveLinkTransponder("1234567", 11L);
        runningRaceWithEntry11(state);

        assertThat(service().link(7L, "1234567", 11L, 3L)).isZero();

        verifyNoInteractions(linkAuditRepository);
        verify(lapTimingService, never()).linkTransponder(anyLong(), any(), anyLong());
    }

    @Test
    void link_ofATransponderLinkedToAnotherEntry_isRefused() {
        LiveRaceState state = new LiveRaceState();
        state.retroactiveLinkTransponder("1234567", 12L);
        runningRaceWithEntry11(state);

        assertThatThrownBy(() -> service().link(7L, "1234567", 11L, 3L))
                .isInstanceOf(StateConflictException.class);

        verifyNoInteractions(linkAuditRepository);
        verify(lapTimingService, never()).linkTransponder(anyLong(), any(), anyLong());
    }

    @Test
    void link_savesTheNumberOnTheEntry_replacingAnUnseenPrimary() {
        runningRaceWithEntry11(new LiveRaceState());
        entry11("1111111", null);
        when(lapTimingService.countPassingsForTransponder(7L, "1111111")).thenReturn(0);

        service().link(7L, "1234567", 11L, 3L);

        verify(swapService).swap(5L, 11L, TransponderSlot.PRIMARY, "1234567", 3L);
    }

    @Test
    void link_savesTheNumberAsSecondary_whenThePrimaryIsBeingSeen() {
        runningRaceWithEntry11(new LiveRaceState());
        entry11("1111111", null);
        when(lapTimingService.countPassingsForTransponder(7L, "1111111")).thenReturn(3);

        service().link(7L, "1234567", 11L, 3L);

        verify(swapService).swap(5L, 11L, TransponderSlot.SECONDARY, "1234567", 3L);
    }

    @Test
    void link_withTwoNumbers_replacesTheOneThatHasNotPassed() {
        runningRaceWithEntry11(new LiveRaceState());
        entry11("1111111", "2222222");
        when(lapTimingService.countPassingsForTransponder(7L, "1111111")).thenReturn(0);
        when(lapTimingService.countPassingsForTransponder(7L, "2222222")).thenReturn(4);

        service().link(7L, "1234567", 11L, 3L);

        verify(swapService).swap(5L, 11L, TransponderSlot.PRIMARY, "1234567", 3L);
    }

    @Test
    void link_ofANumberTheEntryAlreadyHas_changesNothingOnTheEntry() {
        runningRaceWithEntry11(new LiveRaceState());
        entry11("1234567", null);

        service().link(7L, "1234567", 11L, 3L);

        verifyNoInteractions(swapService);
    }

    @Test
    void link_whenTheEntryCannotTakeTheNumber_stillLinksForTheRace() {
        runningRaceWithEntry11(new LiveRaceState());
        entry11("1111111", null);
        when(swapService.swap(anyLong(), anyLong(), any(), any(), anyLong()))
                .thenReturn(new SwapResult.TransponderAlreadyAssigned());

        service().link(7L, "1234567", 11L, 3L);

        verify(lapTimingService).linkTransponder(7L, "1234567", 11L);
    }

    private void entry11(String primary, String secondary) {
        when(labels.eventOf(7L)).thenReturn(5L);
        Entry entry = new Entry();
        entry.setId(11L);
        entry.setTransponderNumberSnapshot(primary);
        entry.setSecondaryTransponderNumber(secondary);
        when(entryRepository.findById(11L)).thenReturn(Optional.of(entry));
    }

    private void runningRaceWithEntry11(LiveRaceState state) {
        when(raceRepository.getOrThrow(7L)).thenReturn(race(RaceStatus.RUNNING));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(List.of(raceEntry(11L)));
        when(lapTimingService.stateFor(7L)).thenReturn(state);
    }

    private static Race race(RaceStatus status) {
        Race race = new Race();
        race.setId(7L);
        race.setStatus(status);
        return race;
    }

    private static RaceEntry raceEntry(long entryId) {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setRaceId(7L);
        raceEntry.setEntryId(entryId);
        return raceEntry;
    }
}
