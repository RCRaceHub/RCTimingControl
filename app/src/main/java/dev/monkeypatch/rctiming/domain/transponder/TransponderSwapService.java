package dev.monkeypatch.rctiming.domain.transponder;

import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLog;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLogRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Swaps an entry's primary or secondary transponder on the day (L11). Every change is written to the entry audit log as
 * TRANSPONDER_SWAP. Lap timing looks transponders up on each passing, so the new number
 * counts from the next passing.
 */
@Service
public class TransponderSwapService {

    public static final String AUDIT_ACTION = "TRANSPONDER_SWAP";

    private final EventRepository eventRepository;
    private final EntryRepository entryRepository;
    private final EntryAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public TransponderSwapService(EventRepository eventRepository,
                                  EntryRepository entryRepository,
                                  EntryAuditLogRepository auditLogRepository,
                                  ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Swaps run one at a time, since write transactions share the single write connection, so two
     * officials cannot both give the same free number to different competitors.
     *
     * <p>Refusals come back as a {@link SwapResult} rather than an exception, and nothing is saved for them. The
     * number is refused if another competitor's active entry in the event holds it; one competitor may share a number
     * between their own entries. Swapping to the number the entry already has succeeds without changing anything.
     *
     * @param newNumber the new number; blank removes a secondary, third or fourth transponder
     */
    @Transactional
    public SwapResult swap(long eventId, long entryId, TransponderSlot slot, String newNumber, long actingUserId) {
        if (eventRepository.findByIdForUpdate(eventId).isEmpty()) {
            return new SwapResult.EntryNotFound();
        }
        Entry entry = entryRepository.findByIdForUpdate(entryId).orElse(null);
        if (entry == null || !Objects.equals(entry.getEventId(), eventId)) {
            return new SwapResult.EntryNotFound();
        }
        if (entry.getStatus() == EntryStatus.WITHDRAWN) {
            return new SwapResult.EntryWithdrawn();
        }

        String normalized = newNumber == null || newNumber.isBlank() ? null : newNumber.trim();
        if (normalized == null && slot == TransponderSlot.PRIMARY) {
            return new SwapResult.PrimaryRequired();
        }

        String oldNumber = numberIn(entry, slot);
        if (Objects.equals(oldNumber, normalized)) {
            return new SwapResult.Success(entry, slot, oldNumber, normalized);
        }

        if (normalized != null) {
            for (TransponderSlot other : TransponderSlot.values()) {
                if (other != slot && normalized.equals(numberIn(entry, other))) {
                    return new SwapResult.SameAsOtherSlot();
                }
            }
            if (heldByAnotherCompetitor(entry, normalized)) {
                return new SwapResult.TransponderAlreadyAssigned();
            }
        }

        // Swapping to the number the imported file has settles a difference an import flagged (#50)
        if (slot == TransponderSlot.PRIMARY) {
            entry.setTransponderNumberSnapshot(normalized);
            if (Objects.equals(normalized, entry.getImportedTransponderNumber())) {
                entry.setImportedTransponderNumber(null);
            }
        } else if (slot == TransponderSlot.SECONDARY) {
            entry.setSecondaryTransponderNumber(normalized);
            if (Objects.equals(normalized, entry.getImportedSecondaryTransponderNumber())) {
                entry.setImportedSecondaryTransponderNumber(null);
            }
        } else if (slot == TransponderSlot.THIRD) {
            entry.setThirdTransponderNumber(normalized);
        } else {
            entry.setFourthTransponderNumber(normalized);
        }
        Instant now = Instant.now();
        entryRepository.save(entry);
        writeAudit(entry.getId(), actingUserId, slot, oldNumber, normalized, now);
        return new SwapResult.Success(entry, slot, oldNumber, normalized);
    }

    /**
     * The slots of each of the event's entries that have been swapped on the day, by entry id. A re-import keeps
     * these numbers (#50).
     */
    @ReadTransaction
    public Map<Long, Set<TransponderSlot>> swappedSlots(long eventId) {
        Map<Long, Set<TransponderSlot>> slots = new HashMap<>();
        for (EntryAuditLog log : auditLogRepository.findByEventIdAndAction(eventId, AUDIT_ACTION)) {
            slotOf(log).ifPresent(slot ->
                    slots.computeIfAbsent(log.getEntryId(), id -> EnumSet.noneOf(TransponderSlot.class)).add(slot));
        }
        return slots;
    }

    private Optional<TransponderSlot> slotOf(EntryAuditLog log) {
        String snapshot = log.getAfterSnapshot() != null ? log.getAfterSnapshot() : log.getBeforeSnapshot();
        if (snapshot == null) {
            return Optional.empty();
        }
        try {
            String slot = objectMapper.readTree(snapshot).path("slot").asString(null);
            return slot == null ? Optional.empty() : Optional.of(TransponderSlot.valueOf(slot));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * A competitor racing two classes may share one transponder across their entries, so only
     * another competitor's live entry in the same event blocks the number.
     */
    private boolean heldByAnotherCompetitor(Entry entry, String number) {
        return entryRepository.findByEventId(entry.getEventId()).stream()
                .filter(other -> !other.getId().equals(entry.getId()))
                .filter(other -> other.getStatus() != EntryStatus.WITHDRAWN)
                .filter(other -> !Objects.equals(other.getCompetitorId(), entry.getCompetitorId()))
                .anyMatch(other -> other.hasTransponder(number));
    }

    private static String numberIn(Entry entry, TransponderSlot slot) {
        return switch (slot) {
            case PRIMARY -> entry.getTransponderNumberSnapshot();
            case SECONDARY -> entry.getSecondaryTransponderNumber();
            case THIRD -> entry.getThirdTransponderNumber();
            case FOURTH -> entry.getFourthTransponderNumber();
        };
    }

    private void writeAudit(Long entryId, long actingUserId, TransponderSlot slot,
                            String oldNumber, String newNumber, Instant at) {
        auditLogRepository.save(EntryAuditLog.of(entryId, actingUserId, AUDIT_ACTION, null,
                json(slot, oldNumber), json(slot, newNumber), at));
    }

    private String json(TransponderSlot slot, String number) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("slot", slot.name());
        m.put("transponderNumber", number);
        return EntryAuditLog.snapshot(objectMapper, m);
    }
}
