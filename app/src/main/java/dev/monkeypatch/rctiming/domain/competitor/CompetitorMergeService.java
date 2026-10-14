package dev.monkeypatch.rctiming.domain.competitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.ExternalSources;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipExclusionRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLog;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLogRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges a duplicate competitor into the one to keep (#123). Results, live timing and championship
 * standings group by competitor, so a driver who exists twice has their history and points split; a
 * merge moves everything onto one record.
 * <p>
 * Only two tables point at a competitor: {@code entries} (results and championship points are derived
 * from entries) and {@code championship_exclusions}. Both move, along with the duplicate's change history, and the duplicate is then deleted, all
 * in one transaction. Each moved entry gets an audit row.
 * <p>
 * What the kept competitor ends up with: its own display name; its BRCA number, home club and spoken
 * name, filled from the duplicate where it has none; and the external identity (the id an import matches
 * by) of whichever has the RaceHub one, so the next import still finds the competitor.
 */
@Service
public class CompetitorMergeService {

    private static final Logger log = LoggerFactory.getLogger(CompetitorMergeService.class);
    public static final String AUDIT_ACTION = "COMPETITOR_MERGED";

    private final CompetitorRepository competitorRepository;
    private final EntryRepository entryRepository;
    private final EntryAuditLogRepository auditLogRepository;
    private final CompetitorAuditLogRepository competitorAuditLogRepository;
    private final ChampionshipExclusionRepository exclusionRepository;
    private final EventClassRepository eventClassRepository;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public CompetitorMergeService(CompetitorRepository competitorRepository,
                                  EntryRepository entryRepository,
                                  EntryAuditLogRepository auditLogRepository,
                                  CompetitorAuditLogRepository competitorAuditLogRepository,
                                  ChampionshipExclusionRepository exclusionRepository,
                                  EventClassRepository eventClassRepository,
                                  ObjectMapper objectMapper,
                                  AuditService audit) {
        this.competitorRepository = competitorRepository;
        this.entryRepository = entryRepository;
        this.auditLogRepository = auditLogRepository;
        this.competitorAuditLogRepository = competitorAuditLogRepository;
        this.exclusionRepository = exclusionRepository;
        this.eventClassRepository = eventClassRepository;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /** One of the two competitors, as the preview shows it. */
    public record Side(Long id, String displayName, String brcaNumber, String homeClub, String spokenName,
                       String externalSource, int entries) {}

    /**
     * What a merge would do. {@code blockers} are reasons it can't go ahead; {@code warnings} are things
     * to know and don't stop it.
     */
    public record Preview(Side keep, Side duplicate, int entriesToMove, int eventsAffected,
                          int exclusionsToMove, String resultingSpokenName, String resultingExternalSource,
                          List<String> warnings, List<String> blockers) {
        @JsonProperty("canMerge")
        public boolean canMerge() {
            return blockers.isEmpty();
        }
    }

    public record Result(Long keptCompetitorId, int entriesMoved, int exclusionsMoved) {}

    @Transactional(readOnly = true)
    public Preview preview(Long keepId, Long duplicateId) {
        requireDistinct(keepId, duplicateId);
        Competitor keep = load(keepId);
        Competitor duplicate = load(duplicateId);
        return plan(keep, duplicate, entryRepository.findByCompetitorId(duplicateId),
                entryRepository.findByCompetitorId(keepId));
    }

    @Transactional
    public Result merge(Long keepId, Long duplicateId, Long adminUserId) {
        requireDistinct(keepId, duplicateId);
        Competitor keep = load(keepId);
        Competitor duplicate = load(duplicateId);
        List<Entry> moving = entryRepository.findByCompetitorId(duplicateId);
        Preview preview = plan(keep, duplicate, moving, entryRepository.findByCompetitorId(keepId));
        if (!preview.canMerge()) {
            throw new CompetitorMergeRefusedException(preview.blockers());
        }

        Map<String, Object> keepBefore = details(keep);
        Map<String, Object> duplicateBefore = details(duplicate);
        Instant now = Instant.now();
        String reason = "Merged duplicate competitor " + duplicate.getDisplayName() + " (#" + duplicate.getId()
                + ") into " + keep.getDisplayName() + " (#" + keep.getId() + ")";
        for (Entry entry : moving) {
            entry.setCompetitorId(keepId);
            entryRepository.save(entry);
            writeAudit(entry.getId(), adminUserId, reason, snapshot(duplicate), snapshot(keep), now);
        }
        int exclusions = exclusionRepository.moveDriver(duplicateId, keepId);

        // The duplicate's change history goes with the competitor that is kept
        competitorAuditLogRepository.moveCompetitor(duplicateId, keepId);

        // The external id is unique, so the duplicate lets go of it before the kept competitor takes it
        boolean takesIdentity = duplicateIdentityWins(keep, duplicate);
        String movedSource = duplicate.getExternalSource();
        String movedId = duplicate.getExternalId();
        if (takesIdentity) {
            duplicate.setExternalSource(null);
            duplicate.setExternalId(null);
            competitorRepository.save(duplicate);
            keep.setExternalSource(movedSource);
            keep.setExternalId(movedId);
        }
        if (isBlank(keep.getBrcaNumber())) {
            keep.setBrcaNumber(duplicate.getBrcaNumber());
        }
        if (isBlank(keep.getHomeClub())) {
            keep.setHomeClub(duplicate.getHomeClub());
        }
        if (isBlank(keep.getSpokenName())) {
            keep.setSpokenName(duplicate.getSpokenName());
        }
        competitorRepository.save(keep);
        competitorRepository.deleteById(duplicateId);

        // The duplicate is deleted, so this row is the only place its details survive
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("kept", keepBefore);
        before.put("duplicate", duplicateBefore);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("kept", details(keep));
        after.put("entriesMoved", moving.stream().map(Entry::getId).toList());
        after.put("exclusionsMoved", exclusions);
        audit.entry(Actor.official(adminUserId), AUDIT_ACTION).entity("competitor", keepId)
                .summary(reason + ": " + moving.size() + " entries and " + exclusions + " championship exclusions moved")
                .before(before).after(after).record();

        log.info("{} by user {}: {} entries and {} championship exclusions moved",
                reason, adminUserId, moving.size(), exclusions);
        return new Result(keepId, moving.size(), exclusions);
    }

    private static void requireDistinct(Long keepId, Long duplicateId) {
        if (Objects.equals(keepId, duplicateId)) {
            throw new IllegalArgumentException("Choose two different competitors to merge");
        }
    }

    private Competitor load(Long id) {
        return competitorRepository.getOrThrow(id);
    }

    private Preview plan(Competitor keep, Competitor duplicate, List<Entry> moving, List<Entry> keepEntries) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (ExternalSources.RACEHUB.equals(keep.getExternalSource())
                && ExternalSources.RACEHUB.equals(duplicate.getExternalSource())) {
            blockers.add(keep.getDisplayName() + " and " + duplicate.getDisplayName() + " are linked to different "
                    + "RaceHub drivers, so they are different people as far as RaceHub is concerned. "
                    + "They can't be merged.");
        }
        blockers.addAll(classConflicts(duplicate, moving, keepEntries));

        if (duplicateIdentityWins(keep, duplicate)) {
            warnings.add(duplicate.getDisplayName() + "'s " + sourceName(duplicate.getExternalSource())
                    + " link moves to " + keep.getDisplayName() + ", so the next import still finds them.");
        } else if (duplicate.getExternalId() != null) {
            warnings.add(duplicate.getDisplayName() + "'s " + sourceName(duplicate.getExternalSource())
                    + " link is dropped. " + keep.getDisplayName() + " keeps theirs.");
        }
        if (!isBlank(keep.getBrcaNumber()) && !isBlank(duplicate.getBrcaNumber())
                && !keep.getBrcaNumber().equals(duplicate.getBrcaNumber())) {
            warnings.add("Their BRCA numbers differ (" + keep.getBrcaNumber() + " and "
                    + duplicate.getBrcaNumber() + "). " + keep.getBrcaNumber() + " is kept.");
        }

        int events = (int) moving.stream().map(Entry::getEventId).distinct().count();
        int exclusions = exclusionRepository.countByDriverId(duplicate.getId());
        String spoken = isBlank(keep.getSpokenName()) ? duplicate.getSpokenName() : keep.getSpokenName();
        String source = duplicateIdentityWins(keep, duplicate) ? duplicate.getExternalSource() : keep.getExternalSource();
        return new Preview(side(keep, keepEntries.size()), side(duplicate, moving.size()), moving.size(), events,
                exclusions, isBlank(spoken) ? null : spoken, source, warnings, blockers);
    }

    /** Both competitors can't hold an active entry in the same class of the same event. */
    private List<String> classConflicts(Competitor duplicate, List<Entry> moving, List<Entry> keepEntries) {
        // An entry with no class can't clash: the one-active-entry index treats missing classes as distinct
        Set<List<Long>> keepSlots = keepEntries.stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN && e.getEventClassId() != null)
                .map(e -> List.of(e.getEventId(), e.getEventClassId()))
                .collect(Collectors.toSet());
        List<Long> conflictingClasses = moving.stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN && e.getEventClassId() != null)
                .filter(e -> keepSlots.contains(List.of(e.getEventId(), e.getEventClassId())))
                .map(Entry::getEventClassId)
                .distinct()
                .toList();
        if (conflictingClasses.isEmpty()) {
            return List.of();
        }
        Map<Long, EventClassRepository.EventClassNames> names = eventClassRepository.namesByIds(conflictingClasses);
        return conflictingClasses.stream()
                .map(id -> "Both have an active entry in " + label(names.get(id), id) + ". Withdraw one of them first.")
                .toList();
    }

    private static String label(EventClassRepository.EventClassNames names, Long eventClassId) {
        return names == null ? "class " + eventClassId : names.racingClassName() + " at " + names.eventName();
    }

    /** True when the duplicate's external id should end up on the kept competitor. */
    private static boolean duplicateIdentityWins(Competitor keep, Competitor duplicate) {
        if (isBlank(duplicate.getExternalId())) {
            return false;
        }
        if (isBlank(keep.getExternalId())) {
            return true;
        }
        return ExternalSources.RACEHUB.equals(duplicate.getExternalSource())
                && !ExternalSources.RACEHUB.equals(keep.getExternalSource());
    }

    private static String sourceName(String source) {
        return ExternalSources.RACEHUB.equals(source) ? "RaceHub" : "import";
    }

    private static Side side(Competitor c, int entries) {
        return new Side(c.getId(), c.getDisplayName(), c.getBrcaNumber(), c.getHomeClub(), c.getSpokenName(),
                c.getExternalSource(), entries);
    }

    private static Map<String, Object> details(Competitor c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("displayName", c.getDisplayName());
        m.put("brcaNumber", c.getBrcaNumber());
        m.put("homeClub", c.getHomeClub());
        m.put("spokenName", c.getSpokenName());
        m.put("externalSource", c.getExternalSource());
        m.put("externalId", c.getExternalId());
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String snapshot(Competitor c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("competitorId", String.valueOf(c.getId()));
        m.put("displayName", c.getDisplayName());
        return EntryAuditLog.snapshot(objectMapper, m);
    }

    private void writeAudit(Long entryId, Long adminId, String reason, String before, String after, Instant now) {
        auditLogRepository.save(EntryAuditLog.of(entryId, adminId, AUDIT_ACTION, reason, before, after, now));
    }
}
