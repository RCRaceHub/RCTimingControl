package dev.monkeypatch.rctiming.domain.checkin;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Check-in at the desk on the day (L11).
 * Check-in here is authoritative; RaceHub's arrival mark is never changed by it.
 */
@Service
public class CheckInService {

    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final AuditService audit;

    public CheckInService(EntryRepository entryRepository, CompetitorRepository competitorRepository,
                          AuditService audit) {
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.audit = audit;
    }

    /**
     * Checks an entry in. Idempotent: a repeat confirm keeps the first check-in time and
     * reports {@code alreadyCheckedIn}. Write transactions run one at a time on the single
     * write connection, so two desks confirming at once cannot both record a first check-in.
     */
    @Transactional
    public CheckInResult confirm(long eventId, long entryId, long actingUserId) {
        Entry entry = entryRepository.findByIdForUpdate(entryId).orElse(null);
        if (entry == null || !Objects.equals(entry.getEventId(), eventId)) {
            return new CheckInResult.NotFound();
        }
        if (entry.getStatus() == EntryStatus.WITHDRAWN) {
            return new CheckInResult.Withdrawn();
        }
        if (entry.getCheckedInAt() != null) {
            return new CheckInResult.Success(entry, true);
        }
        Instant now = Instant.now();
        entry.setCheckedInAt(now);
        entry.setCheckedInByUserId(actingUserId);
        Entry saved = entryRepository.save(entry);
        // Only the first check-in is a change; a repeat confirm keeps it and records nothing
        String name = entry.getCompetitorId() == null ? null
                : competitorRepository.findById(entry.getCompetitorId()).map(Competitor::getDisplayName).orElse(null);
        audit.entry(Actor.official(actingUserId), "ENTRY_CHECKED_IN")
                .entity("entry", entryId).event(eventId)
                .summary("Checked in " + (name == null ? "entry " + entryId : name))
                .after(now.toString()).record();
        return new CheckInResult.Success(saved, false);
    }
}
