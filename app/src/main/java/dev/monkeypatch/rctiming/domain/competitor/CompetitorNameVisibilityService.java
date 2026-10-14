package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets how a competitor's name is shown on public screens, for example initials only for a junior (#265). */
@Service
public class CompetitorNameVisibilityService {

    public static final String AUDIT_ACTION = "COMPETITOR_NAME_VISIBILITY_CHANGED";

    private final CompetitorRepository competitors;
    private final AuditService audit;

    public CompetitorNameVisibilityService(CompetitorRepository competitors, AuditService audit) {
        this.competitors = competitors;
        this.audit = audit;
    }

    /**
     * Saving the value a competitor already has records nothing. The audit row names the competitor by id and
     * holds only the two settings, so a hidden name does not end up in the log.
     */
    @Transactional
    public Competitor set(Long competitorId, NameVisibility visibility, Long actorUserId) {
        Competitor competitor = competitors.getOrThrow(competitorId);
        NameVisibility before = competitor.getNameVisibility();
        if (before == visibility) {
            return competitor;
        }
        competitor.setNameVisibility(visibility);
        Competitor saved = competitors.save(competitor);
        audit.entry(Actor.official(actorUserId), AUDIT_ACTION).entity("competitor", competitorId)
                .summary("Set the public name of competitor #" + competitorId + " to " + visibility)
                .before(before).after(visibility)
                .record();
        return saved;
    }
}
