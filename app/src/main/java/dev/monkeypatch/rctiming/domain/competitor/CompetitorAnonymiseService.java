package dev.monkeypatch.rctiming.domain.competitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultsOutbox.RESULTS_OUTBOX;

/**
 * Anonymises one competitor on request (#263): the competitor stays, so their results, laps and championship points
 * still add up, but nothing that says who they are is kept.
 * <p>
 * What goes: the display name (replaced by {@code Anonymised competitor #id}), BRCA number, RaceHub driver id, home
 * club and spoken name; the name inside every stored result and every queued or sent RaceHub export; and the names and
 * numbers in the change history of the competitor and of their entries, in the audit log and in the entry audit log.
 * Those history rows stay, with their details replaced, so the log still shows that something happened.
 * <p>
 * What stays, because it is not the competitor's own data or is not ours to rewrite: free text officials typed
 * (incident reports, penalty reasons, marshal notes); a held RaceHub document waiting to be applied; copies
 * already sent to RaceHub or to the live feed relay, and older database backups; and finished races still held in
 * memory, boards or announcer clips until they refresh. See {@code docs/data-protection.md}.
 * The competitor's RaceHub id is dropped from the export payloads too. A later import of the same booking is not
 * blocked: a newer version of an imported entry attaches it to a new competitor with the real name.
 */
@Service
public class CompetitorAnonymiseService {

    public static final String AUDIT_ACTION = "COMPETITOR_ANONYMISED";
    static final String REMOVED = "Details removed (competitor anonymised)";
    private static final Set<String> NAME_FIELDS = Set.of("driverName", "displayName", "spokenName");
    /** Fields of a RaceHub export row that identify the person there. */
    private static final Set<String> IDENTITY_FIELDS = Set.of("driverProfileId", "externalSource");
    /** In a result snapshot the ids are RCTC's own. */
    private static final Ids SNAPSHOT_IDS = new Ids("competitorId", "entryId");
    /** In a RaceHub export {@code entryId} is RaceHub's, so only the {@code rctc} ids are trusted. */
    private static final Ids EXPORT_IDS = new Ids("rctcCompetitorId", "rctcEntryId");

    private record Ids(String competitor, String entry) {}
    private static final List<String> LIVE_STATUSES =
            List.of(RaceStatus.GRID.name(), RaceStatus.RUNNING.name(), RaceStatus.STOPPED.name());

    /** What one anonymisation changed, as counts only. */
    public record Result(Long competitorId, int entries, int resultSnapshots, int exportPayloads,
                         int competitorHistoryRows, int entryHistoryRows, int auditRows) {}

    private final CompetitorRepository competitors;
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public CompetitorAnonymiseService(CompetitorRepository competitors, DSLContext dsl, ObjectMapper objectMapper,
                                      AuditService audit) {
        this.competitors = competitors;
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /** The display name an anonymised competitor is given. */
    public static String anonymisedName(Long competitorId) {
        return "Anonymised competitor #" + competitorId;
    }

    /**
     * Removes everything that identifies the competitor, in one write transaction, and records {@code
     * COMPETITOR_ANONYMISED} with counts only. It rewrites stored result snapshots, queued and sent RaceHub export
     * payloads, and the competitor's and their entries' histories.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if there is no such competitor (HTTP 404) @throws
     * dev.monkeypatch.rctiming.domain.StateConflictException if an entry of theirs is in a race on the grid, running or
     * stopped (HTTP 409), so the live screens do not keep the name
     */
    @Transactional
    public Result anonymise(Long competitorId, Long adminUserId) {
        Competitor competitor = competitors.getOrThrow(competitorId);
        List<Long> entryIds = dsl.select(ENTRIES.ID).from(ENTRIES)
                .where(ENTRIES.COMPETITOR_ID.eq(competitorId)).fetch(ENTRIES.ID);
        refuseWhileRacing(entryIds);
        String name = anonymisedName(competitorId);
        String oldName = competitor.getDisplayName();
        String oldSpokenName = competitor.getSpokenName();

        competitor.setDisplayName(name);
        competitor.setBrcaNumber(null);
        competitor.setExternalSource(null);
        competitor.setExternalId(null);
        competitor.setHomeClub(null);
        competitor.setSpokenName(null);
        competitors.save(competitor);

        int snapshots = rewriteSnapshots(competitorId, Set.copyOf(entryIds), name);
        int payloads = rewriteOutbox(competitorId, Set.copyOf(entryIds), name);
        int competitorHistory = dsl.update(COMPETITOR_AUDIT_LOG)
                .set(COMPETITOR_AUDIT_LOG.BEFORE_VALUE, (String) null)
                .set(COMPETITOR_AUDIT_LOG.AFTER_VALUE, (String) null)
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(competitorId)).execute();
        int entryHistory = entryIds.isEmpty() ? 0 : dsl.update(ENTRY_AUDIT_LOG)
                .set(ENTRY_AUDIT_LOG.REASON, REMOVED)
                .set(ENTRY_AUDIT_LOG.BEFORE_SNAPSHOT, (String) null)
                .set(ENTRY_AUDIT_LOG.AFTER_SNAPSHOT, (String) null)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.in(entryIds)).execute();
        int auditRows = scrubAuditLog(competitorId, entryIds, oldName, oldSpokenName);

        Result result = new Result(competitorId, entryIds.size(), snapshots, payloads, competitorHistory,
                entryHistory, auditRows);
        // Counts and the id only: the row must not carry anything that was just removed
        audit.entry(Actor.official(adminUserId), AUDIT_ACTION).entity("competitor", competitorId)
                .summary("Anonymised competitor #" + competitorId)
                .after(result)
                .record();
        return result;
    }

    private void refuseWhileRacing(List<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return;
        }
        boolean racing = dsl.fetchExists(dsl.selectOne().from(RACE_ENTRIES).join(RACES)
                .on(RACES.ID.eq(RACE_ENTRIES.RACE_ID))
                .where(RACE_ENTRIES.ENTRY_ID.in(entryIds).and(RACES.STATUS.in(LIVE_STATUSES))));
        if (racing) {
            throw new StateConflictException("This competitor is in a race that is on the grid, running or stopped. "
                    + "Finish or abandon it first, so the live screens do not keep the name.");
        }
    }

    private int rewriteSnapshots(Long competitorId, Set<Long> entryIds, String name) {
        if (entryIds.isEmpty()) {
            return 0;
        }
        var rows = dsl.select(RESULT_SNAPSHOTS.ID, RESULT_SNAPSHOTS.POSITIONS_JSON,
                        RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON)
                .from(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.in(dsl.select(RACE_ENTRIES.RACE_ID).from(RACE_ENTRIES)
                        .where(RACE_ENTRIES.ENTRY_ID.in(entryIds))))
                .fetch();
        int changed = 0;
        for (var row : rows) {
            String positions = rewrite(row.get(RESULT_SNAPSHOTS.POSITIONS_JSON), SNAPSHOT_IDS, competitorId,
                    entryIds, name);
            String timed = rewrite(row.get(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON), SNAPSHOT_IDS, competitorId,
                    entryIds, name);
            if (positions != null || timed != null) {
                dsl.update(RESULT_SNAPSHOTS)
                        .set(RESULT_SNAPSHOTS.POSITIONS_JSON,
                                positions != null ? positions : row.get(RESULT_SNAPSHOTS.POSITIONS_JSON))
                        .set(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON,
                                timed != null ? timed : row.get(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON))
                        .where(RESULT_SNAPSHOTS.ID.eq(row.get(RESULT_SNAPSHOTS.ID))).execute();
                changed++;
            }
        }
        return changed;
    }

    /**
     * Every export is checked, not just those of the events they entered: a championship's standings are carried
     * in the exports of later rounds too.
     */
    private int rewriteOutbox(Long competitorId, Set<Long> entryIds, String name) {
        int changed = 0;
        for (var row : dsl.select(RESULTS_OUTBOX.ID, RESULTS_OUTBOX.PAYLOAD).from(RESULTS_OUTBOX).fetch()) {
            String payload = rewrite(row.get(RESULTS_OUTBOX.PAYLOAD), EXPORT_IDS, competitorId, entryIds, name);
            if (payload != null) {
                dsl.update(RESULTS_OUTBOX).set(RESULTS_OUTBOX.PAYLOAD, payload)
                        .where(RESULTS_OUTBOX.ID.eq(row.get(RESULTS_OUTBOX.ID))).execute();
                changed++;
            }
        }
        return changed;
    }

    /**
     * Rows about the competitor or their entries, and any row whose text holds their old name: penalties, incident
     * reports, exclusions and practice links are logged under other entities, with the name in the summary.
     */
    private int scrubAuditLog(Long competitorId, List<Long> entryIds, String oldName, String oldSpokenName) {
        Set<String> entryKeys = entryIds.stream().map(String::valueOf).collect(Collectors.toSet());
        var aboutThem = AUDIT_LOG.ENTITY_TYPE.eq("competitor")
                .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(competitorId)));
        if (!entryKeys.isEmpty()) {
            aboutThem = aboutThem.or(AUDIT_LOG.ENTITY_TYPE.eq("entry").and(AUDIT_LOG.ENTITY_ID.in(entryKeys)));
        }
        for (String text : Stream.of(oldName, oldSpokenName).filter(t -> t != null && !t.isBlank()).toList()) {
            aboutThem = aboutThem.or(AUDIT_LOG.SUMMARY.contains(text)).or(AUDIT_LOG.BEFORE_JSON.contains(text))
                    .or(AUDIT_LOG.AFTER_JSON.contains(text));
        }
        return dsl.update(AUDIT_LOG)
                .set(AUDIT_LOG.SUMMARY, REMOVED)
                .set(AUDIT_LOG.BEFORE_JSON, (String) null)
                .set(AUDIT_LOG.AFTER_JSON, (String) null)
                .where(aboutThem).execute();
    }

    /** The JSON with this competitor's names replaced, or null when it held none (or was empty). */
    private String rewrite(String json, Ids ids, Long competitorId, Set<Long> entryIds, String name) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(json);
            return replaceNames(tree, ids, competitorId, entryIds, name) ? objectMapper.writeValueAsString(tree) : null;
        } catch (JsonProcessingException e) {
            // A stored result that can't be read must not be skipped: it could still hold the name
            throw new IllegalStateException("A stored result could not be read, so nothing was anonymised", e);
        }
    }

    private boolean replaceNames(JsonNode node, Ids ids, Long competitorId, Set<Long> entryIds, String name) {
        boolean changed = false;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (isTheirs(object, ids, competitorId, entryIds)) {
                for (String field : NAME_FIELDS) {
                    if (object.hasNonNull(field) && !name.equals(object.get(field).asText())) {
                        object.put(field, name);
                        changed = true;
                    }
                }
                for (String field : IDENTITY_FIELDS) {
                    if (object.hasNonNull(field)) {
                        object.putNull(field);
                        changed = true;
                    }
                }
            }
            for (JsonNode child : object) {
                changed |= replaceNames(child, ids, competitorId, entryIds, name);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                changed |= replaceNames(child, ids, competitorId, entryIds, name);
            }
        }
        return changed;
    }

    private static boolean isTheirs(ObjectNode object, Ids ids, Long competitorId, Set<Long> entryIds) {
        JsonNode competitor = object.get(ids.competitor());
        if (competitor != null && competitor.isNumber() && competitor.asLong() == competitorId) {
            return true;
        }
        JsonNode entry = object.get(ids.entry());
        return entry != null && entry.isNumber() && entryIds.contains(entry.asLong());
    }
}
