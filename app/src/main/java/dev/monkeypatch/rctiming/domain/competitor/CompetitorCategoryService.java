package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Sets the category tags a competitor holds, such as Junior, Vet or F-Grade (#494). A championship can give each tag
 * its own standings. Called on a request thread by {@code AdminCompetitorController}; one write transaction.
 */
@Service
public class CompetitorCategoryService {

    public static final String AUDIT_ACTION = "COMPETITOR_CATEGORIES_CHANGED";

    private final CompetitorRepository competitors;
    private final AuditService audit;

    public CompetitorCategoryService(CompetitorRepository competitors, AuditService audit) {
        this.competitors = competitors;
        this.audit = audit;
    }

    /**
     * Replaces the competitor's tags. Saving the tags a competitor already has records nothing.
     *
     * @throws IllegalArgumentException if the tags break the {@link CategoryTags} limits (HTTP 400)
     */
    @Transactional
    public Competitor set(Long competitorId, List<String> typed, Long actorUserId) {
        List<String> tags = CategoryTags.normalise(typed);
        Competitor competitor = competitors.getOrThrow(competitorId);
        String before = competitor.getCategories();
        String after = CategoryTags.format(tags);
        if (Objects.equals(before, after)) {
            return competitor;
        }
        competitor.setCategories(after);
        Competitor saved = competitors.save(competitor);
        audit.entry(Actor.official(actorUserId), AUDIT_ACTION).entity("competitor", competitorId)
                .summary("Set the categories of competitor #" + competitorId)
                .before(before).after(after)
                .record();
        return saved;
    }
}
