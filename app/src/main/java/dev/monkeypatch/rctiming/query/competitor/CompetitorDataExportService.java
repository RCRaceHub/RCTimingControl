package dev.monkeypatch.rctiming.query.competitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.IncidentReports.INCIDENT_REPORTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAbsences.MARSHAL_ABSENCES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAdjustments.MARSHAL_ADJUSTMENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalPenalties.MARSHAL_PENALTIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Penalties.PENALTIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeDrivers.PRACTICE_DRIVERS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeLaps.PRACTICE_LAPS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;

/**
 * Collects everything RCTC holds about one competitor into a single document, for a request to see a person's data
 * (#263). Each export is recorded in the audit log, with who asked and for whom and none of the data.
 * <p>
 * It covers the competitor, their entries and the events they are in, their result rows, practice drivers and laps,
 * incident reports, penalties, marshal records, championship exclusions and the history rows kept about them. That
 * includes officials' notes and names, so an admin should read it before handing it over.
 */
@Service
public class CompetitorDataExportService {

    public static final String AUDIT_ACTION = "COMPETITOR_EXPORTED";

    private final DSLContext dsl;
    private final CompetitorRepository competitors;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public CompetitorDataExportService(DSLContext dsl, CompetitorRepository competitors, ObjectMapper objectMapper,
                                       AuditService audit) {
        this.dsl = dsl;
        this.competitors = competitors;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /** Runs on the write connection, so the audit row is committed with the read that produced the export. */
    @Transactional
    public Map<String, Object> export(Long competitorId, Long adminUserId) {
        competitors.requireExists(competitorId);
        List<Long> entryIds = dsl.select(ENTRIES.ID).from(ENTRIES)
                .where(ENTRIES.COMPETITOR_ID.eq(competitorId)).fetch(ENTRIES.ID);
        Set<Long> entrySet = Set.copyOf(entryIds);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("note", "Everything RCTC holds about this competitor. Times are UTC. It includes notes "
                + "officials wrote about incidents and penalties, which may mention others. Copies already sent to "
                + "RaceHub or kept in backups are not included.");
        out.put("competitor", dsl.selectFrom(COMPETITORS).where(COMPETITORS.ID.eq(competitorId)).fetchOneMap());
        out.put("entries", dsl.selectFrom(ENTRIES).where(ENTRIES.COMPETITOR_ID.eq(competitorId)).fetchMaps());
        out.put("events", dsl.select(EVENTS.ID, EVENTS.NAME).from(EVENTS)
                .where(EVENTS.ID.in(dsl.select(ENTRIES.EVENT_ID).from(ENTRIES)
                        .where(ENTRIES.COMPETITOR_ID.eq(competitorId)))).fetchMaps());
        out.put("raceEntries", dsl.selectFrom(RACE_ENTRIES).where(RACE_ENTRIES.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("results", resultRows(entrySet));
        out.put("practiceDrivers", dsl.selectFrom(PRACTICE_DRIVERS)
                .where(PRACTICE_DRIVERS.COMPETITOR_ID.eq(competitorId)).fetchMaps());
        out.put("practiceLaps", practiceLaps(competitorId));
        out.put("incidentReports", dsl.selectFrom(INCIDENT_REPORTS)
                .where(INCIDENT_REPORTS.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("penalties", dsl.selectFrom(PENALTIES).where(PENALTIES.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("marshalAdjustments", dsl.selectFrom(MARSHAL_ADJUSTMENTS)
                .where(MARSHAL_ADJUSTMENTS.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("marshalAbsences", dsl.selectFrom(MARSHAL_ABSENCES)
                .where(MARSHAL_ABSENCES.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("marshalPenalties", dsl.selectFrom(MARSHAL_PENALTIES)
                .where(MARSHAL_PENALTIES.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("championshipExclusions", dsl.selectFrom(CHAMPIONSHIP_EXCLUSIONS)
                .where(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID.eq(competitorId)).fetchMaps());
        out.put("competitorHistory", dsl.selectFrom(COMPETITOR_AUDIT_LOG)
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(competitorId)).fetchMaps());
        out.put("entryHistory", dsl.selectFrom(ENTRY_AUDIT_LOG)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.in(entryIds)).fetchMaps());
        out.put("auditLog", auditRows(competitorId, entryIds));

        audit.entry(Actor.official(adminUserId), AUDIT_ACTION).entity("competitor", competitorId)
                .summary("Exported the data held about competitor #" + competitorId)
                .record();
        return out;
    }

    private List<Map<String, Object>> resultRows(Set<Long> entryIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (entryIds.isEmpty()) {
            return rows;
        }
        for (Record snapshot : dsl.select(RESULT_SNAPSHOTS.RACE_ID, RESULT_SNAPSHOTS.FINISHED_AT,
                        RESULT_SNAPSHOTS.POSITIONS_JSON)
                .from(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.in(dsl.select(RACE_ENTRIES.RACE_ID).from(RACE_ENTRIES)
                        .where(RACE_ENTRIES.ENTRY_ID.in(entryIds)))).fetch()) {
            try {
                for (JsonNode row : objectMapper.readTree(snapshot.get(RESULT_SNAPSHOTS.POSITIONS_JSON))) {
                    if (entryIds.contains(row.path("entryId").asLong())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("raceId", snapshot.get(RESULT_SNAPSHOTS.RACE_ID));
                        m.put("finishedAt", snapshot.get(RESULT_SNAPSHOTS.FINISHED_AT));
                        m.put("row", row);
                        rows.add(m);
                    }
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "A stored result could not be read, so the export would be incomplete", e);
            }
        }
        return rows;
    }

    /** The laps of the transponders this competitor was named as in practice sessions. */
    private List<Map<String, Object>> practiceLaps(Long competitorId) {
        List<Map<String, Object>> laps = new ArrayList<>();
        for (Record driver : dsl.select(PRACTICE_DRIVERS.PRACTICE_SESSION_ID, PRACTICE_DRIVERS.TRANSPONDER_NUMBER)
                .from(PRACTICE_DRIVERS).where(PRACTICE_DRIVERS.COMPETITOR_ID.eq(competitorId)).fetch()) {
            laps.addAll(dsl.selectFrom(PRACTICE_LAPS)
                    .where(PRACTICE_LAPS.PRACTICE_SESSION_ID
                            .eq(driver.get(PRACTICE_DRIVERS.PRACTICE_SESSION_ID))
                            .and(PRACTICE_LAPS.TRANSPONDER_NUMBER
                                    .eq(driver.get(PRACTICE_DRIVERS.TRANSPONDER_NUMBER))))
                    .fetchMaps());
        }
        return laps;
    }

    private List<Map<String, Object>> auditRows(Long competitorId, List<Long> entryIds) {
        var about = AUDIT_LOG.ENTITY_TYPE.eq("competitor").and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(competitorId)));
        if (!entryIds.isEmpty()) {
            about = about.or(AUDIT_LOG.ENTITY_TYPE.eq("entry")
                    .and(AUDIT_LOG.ENTITY_ID.in(entryIds.stream().map(String::valueOf).toList())));
        }
        return dsl.selectFrom(AUDIT_LOG).where(about).orderBy(AUDIT_LOG.ID).fetchMaps();
    }
}
