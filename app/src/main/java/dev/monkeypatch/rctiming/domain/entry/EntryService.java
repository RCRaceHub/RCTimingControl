package dev.monkeypatch.rctiming.domain.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.competitor.PossibleDuplicateCompetitorException;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entries added by hand at the venue: a walk-in for someone who did not book through RaceHub, and withdrawing
 * an entry. Entries imported from RaceHub go through {@code domain/racehub} instead.
 *
 * <p>A walk-in is accepted with warnings rather than refused, since the race director is standing at the desk:
 * a transponder another entry in the meeting already uses is allowed, because one competitor may run in
 * several classes. A name close to an existing competitor's stops the entry once, so the official can pick the
 * competitor instead of making a second one (#123).
 */
@Service
@Transactional
public class EntryService {

    /**
     * A walk-in to add: an existing {@code competitorId} or a {@code competitorName} for a new competitor, and
     * {@code confirmNewCompetitor} when a new name that matches someone is a different person (#123).
     */
    public record WalkIn(Long eventId, Long eventClassId, Long competitorId, String competitorName,
                         String primaryTransponder, String secondaryTransponder, Boolean confirmNewCompetitor) {}

    /** The walk-in's entry, and warnings such as a transponder already used in the event. */
    public record WalkInResult(Entry entry, List<String> warnings) {}

    private final EntryRepository entryRepository;
    private final EventRepository eventRepository;
    private final EventClassRepository eventClassRepository;
    private final EntryAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final CompetitorService competitorService;
    private final CompetitorRepository competitorRepository;

    public EntryService(EntryRepository entryRepository,
                        EventRepository eventRepository,
                        EventClassRepository eventClassRepository,
                        EntryAuditLogRepository auditLogRepository,
                        ObjectMapper objectMapper,
                        CompetitorService competitorService,
                        CompetitorRepository competitorRepository) {
        this.entryRepository = entryRepository;
        this.eventRepository = eventRepository;
        this.eventClassRepository = eventClassRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.competitorService = competitorService;
        this.competitorRepository = competitorRepository;
    }

    /**
     * Adds a walk-in entry by hand (L9, #17): an existing competitor or a new one by name, a primary transponder and an
     * optional secondary. The entry is confirmed straight away, has no login and no external source. A transponder
     * another active entry in the event already uses is a warning, not an error. Class membership rules are not
     * checked: staff add walk-ins on the day.
     *
     * @throws dev.monkeypatch.rctiming.domain.StateConflictException if the event is completed, or the competitor
     *         already has an active entry in the class (HTTP 409)
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if the event, class or competitor does not exist
     * @throws IllegalArgumentException if the class belongs to another event, the secondary transponder equals the
     *         primary, or neither or both of an existing competitor and a new name are given
     * @throws dev.monkeypatch.rctiming.domain.competitor.PossibleDuplicateCompetitorException if a new name matches an
     *         existing competitor and {@code confirmNewCompetitor} is not set (HTTP 409)
     */
    public WalkInResult adminCreateEntry(Long adminUserId, WalkIn walkIn) {
        Event event = eventRepository.getOrThrow(walkIn.eventId());
        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new StateConflictException("Event is completed");
        }
        Long ecEventId = eventClassRepository.findEventIdById(walkIn.eventClassId())
                .orElseThrow(() -> new EntityNotFoundException("Event class not found: " + walkIn.eventClassId()));
        if (!walkIn.eventId().equals(ecEventId)) {
            throw new IllegalArgumentException("Event class does not belong to the event");
        }

        String primary = walkIn.primaryTransponder().trim();
        String secondary = walkIn.secondaryTransponder() == null || walkIn.secondaryTransponder().isBlank()
                ? null : walkIn.secondaryTransponder().trim();
        if (primary.equals(secondary)) {
            throw new IllegalArgumentException("The secondary transponder must differ from the primary");
        }

        boolean hasName = walkIn.competitorName() != null && !walkIn.competitorName().isBlank();
        if (walkIn.competitorId() != null && hasName) {
            throw new IllegalArgumentException("Give either an existing competitor or a new name, not both");
        }
        Competitor competitor;
        if (walkIn.competitorId() != null) {
            competitor = competitorRepository.getOrThrow(walkIn.competitorId());
        } else if (hasName) {
            List<Competitor> possible = competitorService.findPossibleDuplicates(walkIn.competitorName());
            if (!possible.isEmpty() && !Boolean.TRUE.equals(walkIn.confirmNewCompetitor())) {
                throw new PossibleDuplicateCompetitorException(possible);
            }
            competitor = competitorService.createWalkIn(walkIn.competitorName());
        } else {
            throw new IllegalArgumentException("Choose a competitor or enter a name");
        }

        List<Entry> eventEntries = entryRepository.findByEventId(event.getId()).stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN)
                .toList();
        boolean duplicate = eventEntries.stream().anyMatch(e ->
                competitor.getId().equals(e.getCompetitorId()) && walkIn.eventClassId().equals(e.getEventClassId()));
        if (duplicate) {
            throw new StateConflictException(competitor.getDisplayName() + " already has an entry in this class");
        }

        Instant now = Instant.now();
        Entry entry = new Entry();
        entry.setCompetitorId(competitor.getId());
        entry.setEventId(event.getId());
        entry.setEventClassId(walkIn.eventClassId());
        entry.setTransponderNumberSnapshot(primary);
        entry.setSecondaryTransponderNumber(secondary);
        entry.setStatus(EntryStatus.CONFIRMED);
        entry.setSubmittedAt(now);
        entry.setConfirmedAt(now);
        Entry persisted = entryRepository.save(entry);

        List<String> warnings = new ArrayList<>();
        for (String number : secondary == null ? List.of(primary) : List.of(primary, secondary)) {
            boolean inUse = eventEntries.stream().anyMatch(e ->
                    number.equals(e.getTransponderNumberSnapshot()) || number.equals(e.getSecondaryTransponderNumber()));
            if (inUse) {
                warnings.add("Transponder " + number + " is already used by another entry in this event.");
            }
        }

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("competitorId", String.valueOf(competitor.getId()));
        created.put("eventClassId", String.valueOf(walkIn.eventClassId()));
        created.put("transponderNumberSnapshot", primary);
        created.put("secondaryTransponderNumber", secondary);
        String afterJson = EntryAuditLog.snapshot(objectMapper, created);
        auditLogRepository.save(
                EntryAuditLog.of(persisted.getId(), adminUserId, "ADMIN_CREATE", null, null, afterJson, now));
        return new WalkInResult(persisted, warnings);
    }

    /**
     * Withdraws an entry. The row is kept with status {@link EntryStatus#WITHDRAWN} and a withdrawn time, and an {@code
     * ADMIN_WITHDRAW} row with the reason goes in the entry audit log.
     *
     * @throws dev.monkeypatch.rctiming.domain.StateConflictException if it is already withdrawn (HTTP 409)
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if there is no such entry (HTTP 404)
     */
    public Entry adminWithdraw(Long entryId, Long adminUserId, String reason) {
        Entry entry = entryRepository.getOrThrow(entryId);
        if (entry.getStatus() == EntryStatus.WITHDRAWN) {
            throw new StateConflictException("Entry already withdrawn");
        }
        String beforeJson = EntryAuditLog.snapshot(objectMapper, Map.of("status", entry.getStatus().name()));
        Instant now = Instant.now();
        entry.setStatus(EntryStatus.WITHDRAWN);
        entry.setWithdrawnAt(now);
        entryRepository.save(entry);
        String afterJson = EntryAuditLog.snapshot(objectMapper, Map.of("status", EntryStatus.WITHDRAWN.name()));
        auditLogRepository.save(EntryAuditLog.of(entry.getId(), adminUserId, "ADMIN_WITHDRAW", reason,
                beforeJson, afterJson, now));
        return entry;
    }
}
