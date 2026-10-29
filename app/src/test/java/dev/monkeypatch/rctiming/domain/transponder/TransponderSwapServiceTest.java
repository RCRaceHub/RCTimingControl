package dev.monkeypatch.rctiming.domain.transponder;

import dev.monkeypatch.rctiming.TestJson;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLog;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLogRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Transponder swap for primary and secondary slots (L11). */
class TransponderSwapServiceTest {

    private static final long EVENT_ID = 1L;

    private EventRepository eventRepository;
    private EntryRepository entryRepository;
    private EntryAuditLogRepository auditLogRepository;
    private TransponderSwapService service;
    private final List<Entry> eventEntries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventRepository = Mockito.mock(EventRepository.class);
        entryRepository = Mockito.mock(EntryRepository.class);
        auditLogRepository = Mockito.mock(EntryAuditLogRepository.class);
        service = new TransponderSwapService(eventRepository, entryRepository, auditLogRepository, TestJson.mapper());
        Mockito.when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(new Event()));
        Mockito.when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(entryRepository.findByEventId(EVENT_ID)).thenReturn(eventEntries);
    }

    private Entry entry(long id, long competitorId, String primary, String secondary) {
        Entry e = new Entry();
        e.setId(id);
        e.setEventId(EVENT_ID);
        e.setCompetitorId(competitorId);
        e.setStatus(EntryStatus.CONFIRMED);
        e.setTransponderNumberSnapshot(primary);
        e.setSecondaryTransponderNumber(secondary);
        eventEntries.add(e);
        Mockito.when(entryRepository.findByIdForUpdate(id)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void swapPrimary_toFreeNumber_updatesEntryAndWritesAudit() {
        Entry e = entry(5L, 50L, "1234567", null);

        SwapResult result = service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, " 7654321 ", 42L);

        assertThat(result).isEqualTo(new SwapResult.Success(e, TransponderSlot.PRIMARY, "1234567", "7654321"));
        assertThat(e.getTransponderNumberSnapshot()).isEqualTo("7654321");

        ArgumentCaptor<EntryAuditLog> audit = ArgumentCaptor.forClass(EntryAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("TRANSPONDER_SWAP");
        assertThat(audit.getValue().getEntryId()).isEqualTo(5L);
        assertThat(audit.getValue().getAdminUserId()).isEqualTo(42L);
        assertThat(audit.getValue().getBeforeSnapshot())
                .isEqualTo("{\"slot\":\"PRIMARY\",\"transponderNumber\":\"1234567\"}");
        assertThat(audit.getValue().getAfterSnapshot())
                .isEqualTo("{\"slot\":\"PRIMARY\",\"transponderNumber\":\"7654321\"}");
    }

    @Test
    void swapSecondary_setsAndThenRemovesIt() {
        Entry e = entry(5L, 50L, "1234567", null);

        service.swap(EVENT_ID, 5L, TransponderSlot.SECONDARY, "2222", 42L);
        assertThat(e.getSecondaryTransponderNumber()).isEqualTo("2222");

        SwapResult removed = service.swap(EVENT_ID, 5L, TransponderSlot.SECONDARY, "  ", 42L);
        assertThat(removed).isEqualTo(new SwapResult.Success(e, TransponderSlot.SECONDARY, "2222", null));
        assertThat(e.getSecondaryTransponderNumber()).isNull();
        verify(auditLogRepository, Mockito.times(2)).save(any());
    }

    @Test
    void swap_toTheCurrentNumber_isANoOp() {
        Entry e = entry(5L, 50L, "1234567", null);

        SwapResult result = service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "1234567", 42L);

        assertThat(result).isEqualTo(new SwapResult.Success(e, TransponderSlot.PRIMARY, "1234567", "1234567"));
        verify(entryRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void swap_toAnotherCompetitorsNumber_isRejectedAndNothingChanges() {
        Entry target = entry(5L, 50L, "1234567", null);
        Entry other = entry(6L, 60L, "9999", "8888");

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "9999", 42L))
                .isInstanceOf(SwapResult.TransponderAlreadyAssigned.class);
        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.SECONDARY, "8888", 42L))
                .isInstanceOf(SwapResult.TransponderAlreadyAssigned.class);

        assertThat(target.getTransponderNumberSnapshot()).isEqualTo("1234567");
        assertThat(target.getSecondaryTransponderNumber()).isNull();
        assertThat(other.getTransponderNumberSnapshot()).isEqualTo("9999");
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void swap_toTheSameCompetitorsOtherEntry_isAllowed() {
        // One driver racing two classes on one transponder
        Entry stock = entry(5L, 50L, "1234567", null);
        entry(6L, 50L, "9999", null);

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "9999", 42L))
                .isInstanceOf(SwapResult.Success.class);
        assertThat(stock.getTransponderNumberSnapshot()).isEqualTo("9999");
    }

    @Test
    void swap_toAWithdrawnEntrysNumber_isAllowed() {
        entry(5L, 50L, "1234567", null);
        entry(6L, 60L, "9999", null).setStatus(EntryStatus.WITHDRAWN);

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "9999", 42L))
                .isInstanceOf(SwapResult.Success.class);
    }

    @Test
    void swap_toTheEntrysOtherSlot_isRejected() {
        entry(5L, 50L, "1234567", "2222");

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "2222", 42L))
                .isInstanceOf(SwapResult.SameAsOtherSlot.class);
    }

    @Test
    void swap_removingThePrimary_isRejected() {
        Entry e = entry(5L, 50L, "1234567", null);

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "", 42L))
                .isInstanceOf(SwapResult.PrimaryRequired.class);
        assertThat(e.getTransponderNumberSnapshot()).isEqualTo("1234567");
    }

    @Test
    void swap_unknownOrOtherEventEntry_isNotFound() {
        Mockito.when(entryRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        Entry elsewhere = entry(5L, 50L, "1234567", null);
        elsewhere.setEventId(2L);

        assertThat(service.swap(EVENT_ID, 9L, TransponderSlot.PRIMARY, "1", 42L))
                .isInstanceOf(SwapResult.EntryNotFound.class);
        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "1", 42L))
                .isInstanceOf(SwapResult.EntryNotFound.class);
    }

    @Test
    void swap_locksTheEventBeforeCheckingForConflicts() {
        entry(5L, 50L, "1234567", null);

        service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "7654321", 42L);

        var order = Mockito.inOrder(eventRepository, entryRepository);
        order.verify(eventRepository).findByIdForUpdate(EVENT_ID);
        order.verify(entryRepository).findByEventId(EVENT_ID);
    }

    @Test
    void swap_unknownEvent_isNotFound() {
        Mockito.when(eventRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThat(service.swap(2L, 5L, TransponderSlot.PRIMARY, "1", 42L))
                .isInstanceOf(SwapResult.EntryNotFound.class);
    }

    @Test
    void swap_withdrawnEntry_isRefused() {
        entry(5L, 50L, "1234567", null).setStatus(EntryStatus.WITHDRAWN);

        assertThat(service.swap(EVENT_ID, 5L, TransponderSlot.PRIMARY, "1", 42L))
                .isInstanceOf(SwapResult.EntryWithdrawn.class);
    }
}
