package dev.monkeypatch.rctiming.query.competitor;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.competitor.SpeechName;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Users.USERS;

/**
 * Reads competitors for the admin pages: the list, one competitor's history of changes, and the groups of
 * competitors whose names are close enough that they may be the same person and worth merging (#123).
 */
@Service
@ReadTransaction
public class CompetitorQueryService {

    private final DSLContext dsl;

    public CompetitorQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Every competitor by display name, ties by id, with the speech name worked out for each. */
    public List<CompetitorSummaryDto> listAll() {
        return dsl.select(COMPETITORS.ID, COMPETITORS.DISPLAY_NAME, COMPETITORS.BRCA_NUMBER, COMPETITORS.HOME_CLUB,
                        COMPETITORS.SPOKEN_NAME, COMPETITORS.NAME_VISIBILITY, COMPETITORS.CATEGORIES)
                .from(COMPETITORS)
                .orderBy(COMPETITORS.DISPLAY_NAME.asc(), COMPETITORS.ID.asc())
                .fetch(r -> new CompetitorSummaryDto(
                        r.get(COMPETITORS.ID),
                        r.get(COMPETITORS.DISPLAY_NAME),
                        r.get(COMPETITORS.BRCA_NUMBER),
                        r.get(COMPETITORS.HOME_CLUB),
                        r.get(COMPETITORS.SPOKEN_NAME),
                        SpeechName.of(r.get(COMPETITORS.SPOKEN_NAME), r.get(COMPETITORS.DISPLAY_NAME)),
                        r.get(COMPETITORS.NAME_VISIBILITY),
                        dev.monkeypatch.rctiming.domain.competitor.CategoryTags.parse(r.get(COMPETITORS.CATEGORIES))));
    }

    /**
     * The transponder numbers on the competitor's newest entry (highest id, any event, class or status), so a walk-in
     * can start from the numbers they used last time. Withdrawn entries count: the car is still theirs. Returns an
     * all-null result when they have no entries.
     */
    public CompetitorTransponderDto lastTransponders(Long competitorId) {
        return dsl.select(ENTRIES.TRANSPONDER_NUMBER, ENTRIES.SECONDARY_TRANSPONDER_NUMBER,
                        ENTRIES.THIRD_TRANSPONDER_NUMBER, ENTRIES.FOURTH_TRANSPONDER_NUMBER)
                .from(ENTRIES)
                .where(ENTRIES.COMPETITOR_ID.eq(competitorId))
                .orderBy(ENTRIES.ID.desc())
                .limit(1)
                .fetchOptional(r -> new CompetitorTransponderDto(
                        r.get(ENTRIES.TRANSPONDER_NUMBER),
                        r.get(ENTRIES.SECONDARY_TRANSPONDER_NUMBER),
                        r.get(ENTRIES.THIRD_TRANSPONDER_NUMBER),
                        r.get(ENTRIES.FOURTH_TRANSPONDER_NUMBER)))
                .orElse(new CompetitorTransponderDto(null, null, null, null));
    }

    /** A competitor's recorded changes, newest first, with the name of the official who made each. */
    public List<CompetitorChangeDto> listChanges(Long competitorId) {
        return dsl.select(COMPETITOR_AUDIT_LOG.CREATED_AT, USERS.FIRST_NAME, USERS.LAST_NAME,
                        COMPETITOR_AUDIT_LOG.ACTION, COMPETITOR_AUDIT_LOG.BEFORE_VALUE, COMPETITOR_AUDIT_LOG.AFTER_VALUE)
                .from(COMPETITOR_AUDIT_LOG)
                .join(USERS).on(USERS.ID.eq(COMPETITOR_AUDIT_LOG.ACTOR_USER_ID))
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(competitorId))
                .orderBy(COMPETITOR_AUDIT_LOG.CREATED_AT.desc(), COMPETITOR_AUDIT_LOG.ID.desc())
                .fetch(r -> new CompetitorChangeDto(
                        r.get(COMPETITOR_AUDIT_LOG.CREATED_AT),
                        (r.get(USERS.FIRST_NAME) + " " + r.get(USERS.LAST_NAME)).trim(),
                        r.get(COMPETITOR_AUDIT_LOG.ACTION),
                        r.get(COMPETITOR_AUDIT_LOG.BEFORE_VALUE),
                        r.get(COMPETITOR_AUDIT_LOG.AFTER_VALUE)));
    }

    /**
     * Competitors that may be one person entered twice: the same name (ignoring case and spacing) or
     * the same BRCA number. A pair that shares both is listed once (#123).
     *
     * <p>Groups come name groups first, then BRCA-only groups, each in the order of the first competitor in the
     * name-sorted list. A group is only reported when it holds two or more competitors. The BRCA number is compared
     * after trimming but case-sensitively; a blank one never matches.
     */
    public List<CompetitorDuplicateGroupDto> listPossibleDuplicates() {
        List<CompetitorSummaryDto> all = listAll();
        Map<String, List<CompetitorSummaryDto>> byName = all.stream()
                .collect(Collectors.groupingBy(c -> Names.matchKey(c.displayName()),
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, List<CompetitorSummaryDto>> byBrca = all.stream()
                .filter(c -> c.brcaNumber() != null && !c.brcaNumber().isBlank())
                .collect(Collectors.groupingBy(c -> c.brcaNumber().trim(), LinkedHashMap::new, Collectors.toList()));

        List<CompetitorDuplicateGroupDto> groups = new ArrayList<>();
        Set<Set<Long>> sharedBoth = new HashSet<>();
        for (List<CompetitorSummaryDto> sameName : byName.values()) {
            if (sameName.size() < 2) continue;
            Set<Long> ids = sameName.stream().map(CompetitorSummaryDto::id).collect(Collectors.toSet());
            boolean sameBrcaToo = byBrca.values().stream()
                    .anyMatch(g -> g.size() >= 2 && g.stream().map(CompetitorSummaryDto::id).collect(Collectors.toSet()).equals(ids));
            if (sameBrcaToo) sharedBoth.add(ids);
            groups.add(new CompetitorDuplicateGroupDto(
                    sameBrcaToo ? "Same name and BRCA number" : "Same name", sameName));
        }
        for (List<CompetitorSummaryDto> sameBrca : byBrca.values()) {
            if (sameBrca.size() < 2) continue;
            if (sharedBoth.contains(sameBrca.stream().map(CompetitorSummaryDto::id).collect(Collectors.toSet()))) continue;
            groups.add(new CompetitorDuplicateGroupDto("Same BRCA number", sameBrca));
        }
        return groups;
    }
}
