package dev.monkeypatch.rctiming.query.resultsexport;

import dev.monkeypatch.rctiming.domain.ExternalSources;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.championship.ChampionshipStandingsQuery;
import dev.monkeypatch.rctiming.query.championship.RoundResultDto;
import dev.monkeypatch.rctiming.query.championship.StandingsRowDto;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportV1;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Championships.CHAMPIONSHIPS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ClubProfiles.CLUB_PROFILES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Penalties.PENALTIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * Builds an event's Results Export v1 document (#27) from its result snapshots, penalties and championship
 * standings. Read-only; the caller decides the revision.
 */
@Component
@ReadTransaction
public class ResultsExportQuery {

    static final String SYSTEM = "RCTimingControl";

    private final DSLContext dsl;
    private final ResultSnapshotJson snapshotJson;
    private final ChampionshipStandingsQuery standingsQuery;

    public ResultsExportQuery(DSLContext dsl, ResultSnapshotJson snapshotJson,
                              ChampionshipStandingsQuery standingsQuery) {
        this.dsl = dsl;
        this.snapshotJson = snapshotJson;
        this.standingsQuery = standingsQuery;
    }

    public ResultsExportV1 build(long eventId, long revision, Instant generatedAt) {
        var event = dsl.select(EVENTS.NAME, EVENTS.EVENT_DATE, EVENTS.STATUS, EVENTS.RACEHUB_EVENT_ID)
                .from(EVENTS)
                .where(EVENTS.ID.eq(eventId))
                .fetchOne();
        if (event == null) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
        String clubName = dsl.select(CLUB_PROFILES.NAME).from(CLUB_PROFILES).limit(1).fetchOne(CLUB_PROFILES.NAME);

        Map<Long, EntryRef> entries = entries(eventId);
        return new ResultsExportV1(
                ResultsExportV1.SCHEMA_VERSION,
                revision,
                generatedAt.toString(),
                new ResultsExportV1.Source(SYSTEM, clubName),
                new ResultsExportV1.EventRef(event.get(EVENTS.RACEHUB_EVENT_ID), eventId, event.get(EVENTS.NAME),
                        String.valueOf(event.get(EVENTS.EVENT_DATE)), event.get(EVENTS.STATUS)),
                races(eventId, entries),
                championships(eventId));
    }

    private List<ResultsExportV1.Race> races(long eventId, Map<Long, EntryRef> entries) {
        var rows = dsl.select(RACES.ID, RACES.EVENT_CLASS_ID, RACES.HEAT_NUMBER, RACES.FINAL_LETTER, RACES.FINISHED_AT,
                        RACES.STARTED_AT, RACES.ABANDONED_AT, ROUNDS.TYPE, ROUNDS.ROUND_NUMBER, RACING_CLASSES.NAME,
                        RESULT_SNAPSHOTS.POSITIONS_JSON, RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON,
                        RESULT_SNAPSHOTS.FINISHED_AT)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .leftJoin(RESULT_SNAPSHOTS).on(RESULT_SNAPSHOTS.RACE_ID.eq(RACES.ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .and(RACES.STATUS.eq("FINISHED"))
                .orderBy(ROUNDS.SEQUENCE_IN_EVENT, RACES.SEQUENCE_IN_ROUND, RACES.ID)
                .fetch();

        Map<Long, List<PenaltyRow>> penalties = penalties(eventId);
        Map<Long, TreeSet<String>> racehubClassIds = racehubClassIds(eventId);
        List<ResultsExportV1.Race> races = new ArrayList<>();
        for (Record r : rows) {
            long raceId = r.get(RACES.ID);
            Instant finishedAt = r.get(RACES.FINISHED_AT) != null ? r.get(RACES.FINISHED_AT)
                    : r.get(RESULT_SNAPSHOTS.FINISHED_AT);
            Map<Long, List<ResultsExportV1.Penalty>> racePenalties =
                    penaltiesByEntry(penalties.getOrDefault(raceId, List.of()), r.get(RACES.STARTED_AT), finishedAt,
                            r.get(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON) != null);
            List<ResultsExportV1.Row> results = new ArrayList<>();
            String positionsJson = r.get(RESULT_SNAPSHOTS.POSITIONS_JSON);
            for (ResultSnapshotDto.ResultRow p : snapshotJson.positions(raceId, positionsJson)) {
                EntryRef entry = entries.get(p.entryId());
                results.add(new ResultsExportV1.Row(
                        p.position(),
                        entry == null ? null : entry.externalSource(),
                        entry == null ? null : entry.externalEntryId(),
                        entry == null ? null : entry.driverProfileId(),
                        entry == null ? null : entry.racehubEventClassId(),
                        p.entryId(),
                        // The entry's competitor now, not the snapshot's: a merge moves entries to another competitor
                        entry != null ? Long.valueOf(entry.competitorId()) : p.competitorId(),
                        p.driverName() != null ? p.driverName() : entry == null ? null : entry.displayName(),
                        p.carNumber(),
                        p.lapsCompleted(),
                        p.totalTimeMs(),
                        p.bestLapMs(),
                        racePenalties.getOrDefault(p.entryId(), List.of())));
            }
            races.add(new ResultsExportV1.Race(
                    raceId,
                    r.get(RACES.EVENT_CLASS_ID),
                    List.copyOf(racehubClassIds.getOrDefault(raceId, new TreeSet<>())),
                    r.get(RACING_CLASSES.NAME),
                    r.get(ROUNDS.TYPE),
                    r.get(ROUNDS.ROUND_NUMBER),
                    r.get(RACES.HEAT_NUMBER),
                    r.get(RACES.FINAL_LETTER),
                    r.get(RACES.ABANDONED_AT) != null ? "ABANDONED" : "FINISHED",
                    finishedAt == null ? null : finishedAt.toString(),
                    results));
        }
        return races;
    }

    /** Penalties per race, in the order they were given. */
    private Map<Long, List<PenaltyRow>> penalties(long eventId) {
        Map<Long, List<PenaltyRow>> byRace = new LinkedHashMap<>();
        dsl.select(PENALTIES.RACE_ID, PENALTIES.ENTRY_ID, PENALTIES.PENALTY_TYPE, PENALTIES.VALUE, PENALTIES.REASON,
                        PENALTIES.APPLIED_AT)
                .from(PENALTIES)
                .join(RACES).on(RACES.ID.eq(PENALTIES.RACE_ID))
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .orderBy(PENALTIES.APPLIED_AT, PENALTIES.ID)
                .forEach(p -> byRace.computeIfAbsent(p.get(PENALTIES.RACE_ID), k -> new ArrayList<>())
                        .add(new PenaltyRow(p.get(PENALTIES.ENTRY_ID), p.get(PENALTIES.PENALTY_TYPE),
                                p.get(PENALTIES.VALUE), p.get(PENALTIES.REASON), p.get(PENALTIES.APPLIED_AT))));
        return byRace;
    }

    /**
     * One race's penalties per entry, and whether the stored result allows for each.
     *
     * <p>A result stored with corrections (#63) allows for every penalty given since the race last started:
     * a LAP penalty during the race came off the live lap count, and the rest are applied when the result is
     * stored or corrected. One from before a restart belongs to the earlier run. A result stored before
     * corrections existed, and not corrected since, allows only for LAP penalties given while the race ran.
     */
    private static Map<Long, List<ResultsExportV1.Penalty>> penaltiesByEntry(List<PenaltyRow> rows,
                                                                            Instant startedAt, Instant finishedAt,
                                                                            boolean storedWithCorrections) {
        Map<Long, List<ResultsExportV1.Penalty>> byEntry = new LinkedHashMap<>();
        for (PenaltyRow p : rows) {
            boolean sinceStart = p.appliedAt() != null && (startedAt == null || !p.appliedAt().isBefore(startedAt));
            boolean included = storedWithCorrections
                    ? sinceStart
                    : PenaltyType.LAP.name().equals(p.type()) && startedAt != null && sinceStart
                            && (finishedAt == null || !p.appliedAt().isAfter(finishedAt));
            byEntry.computeIfAbsent(p.entryId(), k -> new ArrayList<>())
                    .add(new ResultsExportV1.Penalty(p.type(), plain(p.value()), p.reason(), included));
        }
        return byEntry;
    }

    /** The RaceHub classes of everyone on each race's grid, finishers or not. */
    private Map<Long, TreeSet<String>> racehubClassIds(long eventId) {
        Map<Long, TreeSet<String>> byRace = new LinkedHashMap<>();
        dsl.selectDistinct(RACE_ENTRIES.RACE_ID, ENTRIES.RACEHUB_EVENT_CLASS_ID)
                .from(RACE_ENTRIES)
                .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                .join(RACES).on(RACES.ID.eq(RACE_ENTRIES.RACE_ID))
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .and(ENTRIES.RACEHUB_EVENT_CLASS_ID.isNotNull())
                .forEach(r -> byRace.computeIfAbsent(r.get(RACE_ENTRIES.RACE_ID), k -> new TreeSet<>())
                        .add(r.get(ENTRIES.RACEHUB_EVENT_CLASS_ID)));
        return byRace;
    }

    /** 2.0 → 2 and 1.50 → 1.5, never 1E+1. */
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private List<ResultsExportV1.Championship> championships(long eventId) {
        var links = dsl.select(CHAMPIONSHIPS.ID, CHAMPIONSHIPS.NAME, CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER)
                .from(CHAMPIONSHIP_EVENT_LINKS)
                .join(CHAMPIONSHIPS).on(CHAMPIONSHIPS.ID.eq(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID))
                .where(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.eq(eventId))
                .orderBy(CHAMPIONSHIPS.ID)
                .fetch();

        List<ResultsExportV1.Championship> championships = new ArrayList<>();
        for (var link : links) {
            long championshipId = link.get(CHAMPIONSHIPS.ID);
            List<StandingsRowDto> standingsRows = standingsQuery.computeStandings(championshipId);
            Map<Long, CompetitorRef> competitors = competitors(standingsRows);
            Map<Long, List<StandingsRowDto>> byClass = new LinkedHashMap<>();
            for (StandingsRowDto row : standingsRows) {
                byClass.computeIfAbsent(row.racingClassId(), k -> new ArrayList<>()).add(row);
            }
            Map<Long, String> classNames = new LinkedHashMap<>();
            if (!byClass.isEmpty()) {
                dsl.select(RACING_CLASSES.ID, RACING_CLASSES.NAME).from(RACING_CLASSES)
                        .where(RACING_CLASSES.ID.in(byClass.keySet()))
                        .forEach(c -> classNames.put(c.get(RACING_CLASSES.ID), c.get(RACING_CLASSES.NAME)));
            }

            List<ResultsExportV1.ChampionshipClass> classes = new ArrayList<>();
            byClass.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(c -> classes.add(new ResultsExportV1.ChampionshipClass(
                            c.getKey(), classNames.get(c.getKey()), standings(c.getValue(), eventId, competitors))));
            championships.add(new ResultsExportV1.Championship(championshipId, link.get(CHAMPIONSHIPS.NAME),
                    link.get(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER), classes));
        }
        return championships;
    }

    private List<ResultsExportV1.Standing> standings(List<StandingsRowDto> rows, long eventId,
                                                     Map<Long, CompetitorRef> competitors) {
        List<StandingsRowDto> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(StandingsRowDto::totalPoints).reversed()
                .thenComparing(StandingsRowDto::displayName, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ResultsExportV1.Standing> standings = new ArrayList<>();
        int position = 0;
        Integer lastPoints = null;
        for (int i = 0; i < sorted.size(); i++) {
            StandingsRowDto row = sorted.get(i);
            if (!Objects.equals(lastPoints, row.totalPoints())) {
                position = i + 1;
                lastPoints = row.totalPoints();
            }
            RoundResultDto here = row.rounds().stream()
                    .filter(rr -> Objects.equals(rr.eventId(), eventId))
                    .findFirst().orElse(null);
            CompetitorRef competitor = competitors.getOrDefault(row.driverId(), CompetitorRef.NONE);
            standings.add(new ResultsExportV1.Standing(
                    position,
                    competitor.externalSource(),
                    competitor.externalId(),
                    row.driverId(),
                    row.displayName(),
                    row.totalPoints(),
                    here == null || here.position() == 0 ? null : here.position(),
                    here == null ? null : here.points(),
                    here != null && here.dropped(),
                    here != null && here.excluded()));
        }
        return standings;
    }

    /**
     * Each driver's identity, from their competitor record rather than any one entry, so a RaceHub driver who
     * also ran as a walk-in is still named by their RaceHub id.
     */
    private Map<Long, CompetitorRef> competitors(List<StandingsRowDto> rows) {
        Map<Long, CompetitorRef> competitors = new LinkedHashMap<>();
        List<Long> ids = rows.stream().map(StandingsRowDto::driverId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return competitors;
        }
        dsl.select(COMPETITORS.ID, COMPETITORS.EXTERNAL_SOURCE, COMPETITORS.EXTERNAL_ID)
                .from(COMPETITORS)
                .where(COMPETITORS.ID.in(ids))
                .forEach(c -> competitors.put(c.get(COMPETITORS.ID),
                        CompetitorRef.of(c.get(COMPETITORS.EXTERNAL_SOURCE), c.get(COMPETITORS.EXTERNAL_ID))));
        return competitors;
    }

    private Map<Long, EntryRef> entries(long eventId) {
        Map<Long, EntryRef> entries = new LinkedHashMap<>();
        dsl.select(ENTRIES.ID, ENTRIES.EXTERNAL_SOURCE, ENTRIES.EXTERNAL_ENTRY_ID, ENTRIES.RACEHUB_EVENT_CLASS_ID,
                        COMPETITORS.ID, COMPETITORS.DISPLAY_NAME, COMPETITORS.EXTERNAL_ID)
                .from(ENTRIES)
                .join(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .where(ENTRIES.EVENT_ID.eq(eventId))
                .forEach(e -> {
                    // RaceHub only knows its own ids, so an entry from anywhere else (a CSV import, another
                    // booking system) is sent like a walk-in, by its RCTC ids
                    boolean fromRaceHub = ExternalSources.RACEHUB.equals(e.get(ENTRIES.EXTERNAL_SOURCE));
                    entries.put(e.get(ENTRIES.ID), new EntryRef(
                            fromRaceHub ? ExternalSources.RACEHUB : null,
                            fromRaceHub ? e.get(ENTRIES.EXTERNAL_ENTRY_ID) : null,
                            e.get(ENTRIES.RACEHUB_EVENT_CLASS_ID),
                            e.get(COMPETITORS.ID),
                            e.get(COMPETITORS.DISPLAY_NAME),
                            // A driver profile id only means something alongside a RaceHub entry
                            fromRaceHub ? e.get(COMPETITORS.EXTERNAL_ID) : null));
                });
        return entries;
    }

    private record EntryRef(String externalSource, String externalEntryId, String racehubEventClassId,
                            long competitorId, String displayName, String driverProfileId) {
    }

    private record CompetitorRef(String externalSource, String externalId) {
        static final CompetitorRef NONE = new CompetitorRef(null, null);

        /**
         * Only a RaceHub driver is named by their source and id, and only with that id, so a row never claims
         * RaceHub without saying which driver. Any other source is sent like a walk-in.
         */
        static CompetitorRef of(String externalSource, String externalId) {
            return !ExternalSources.RACEHUB.equals(externalSource) || externalId == null
                    ? NONE : new CompetitorRef(externalSource, externalId);
        }
    }

    private record PenaltyRow(long entryId, String type, BigDecimal value, String reason, Instant appliedAt) {
    }
}
