package dev.monkeypatch.rctiming.domain.csvimport;

import dev.monkeypatch.rctiming.domain.ExternalSources;
import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.csvimport.CsvImportResult.Change;
import dev.monkeypatch.rctiming.domain.csvimport.CsvImportResult.Group;
import dev.monkeypatch.rctiming.domain.csvimport.CsvImportResult.UnmappedClass;
import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser.Kind;
import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser.ParsedCsv;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.entryimport.EntryImports;
import dev.monkeypatch.rctiming.domain.entryimport.EventClasses;
import dev.monkeypatch.rctiming.domain.entryimport.FinalState;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Imports an RC-Timing style driver CSV into an event (#39), as a lossy adapter beside the RaceHub
 * import. The file has no entry ids, versions or withdrawals, so nothing is applied on trust:
 * <ul>
 *   <li>Each booked row gets a stable key from its BRCA number, or its name when the BRCA number is
 *       0, and the event class it is placed in, so a class given by name in one file and by number
 *       in the next is the same entry. A driver is matched to an existing competitor from any
 *       source (RaceHub, a walk-in or an earlier CSV) by BRCA number, or by name when neither has
 *       one, so their history stays together; a competitor from another source is never renamed.</li>
 *   <li>The preview sorts rows into new, changed (with old and new values), unchanged and skipped
 *       ({@code update} rows), and lists the entries an earlier CSV import made that this file
 *       leaves out as missing.</li>
 *   <li>On confirm, new rows are created, and only the changed rows and missing entries the
 *       official picked are updated or withdrawn. Withdrawing never deletes.</li>
 *   <li>Only CSV-sourced entries can be missing, so walk-ins and RaceHub entries are never
 *       withdrawn by a CSV.</li>
 * </ul>
 * Classes are placed through the event's class mappings (keyed {@code CSV:<class name>}), then by
 * racing class name, then, for a row with only a Class Number, by the event's Nth class.
 */
@Service
public class CsvImportService {

    private final EventRepository eventRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final EntryImports entryImports;
    private final AuditService audit;

    public CsvImportService(EventRepository eventRepository,
                            EntryRepository entryRepository,
                            CompetitorRepository competitorRepository,
                            EntryImports entryImports,
                            AuditService audit) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.entryImports = entryImports;
        this.audit = audit;
    }

    /** What the official picked in the preview: changed rows to update (by key), missing entries to withdraw. */
    public record Selection(Set<String> update, Set<Long> withdraw) {
        public static final Selection NONE = new Selection(Set.of(), Set.of());

        public Selection {
            update = update == null ? Set.of() : Set.copyOf(update);
            withdraw = withdraw == null ? Set.of() : Set.copyOf(withdraw);
        }
    }

    @Transactional
    public CsvImportResult importCsv(Actor actor, Long eventId, String content, boolean dryRun, Selection selection) {
        Event event = eventRepository.getOrThrow(eventId);
        Selection picked = selection == null ? Selection.NONE : selection;
        ParsedCsv parsed = RcTimingCsvParser.parse(content);
        List<String> errors = new ArrayList<>(parsed.errors());
        List<String> warnings = new ArrayList<>(parsed.warnings());

        Classes classes = new Classes(entryImports.eventClasses(eventId));
        Map<String, UnmappedAccumulator> unmapped = new TreeMap<>();
        Map<String, Competitor> competitors = new HashMap<>();
        List<CsvImportResult.Row> skipped = new ArrayList<>();
        List<Planned> plan = plan(eventId, parsed, classes, unmapped, competitors, skipped, errors);

        Set<String> keys = plan.stream().map(Planned::key).collect(Collectors.toSet());
        List<Entry> missing = entryRepository.findByEventId(eventId).stream()
                .filter(e -> ExternalSources.CSV.equals(e.getExternalSource())
                        && e.getStatus() != EntryStatus.WITHDRAWN)
                .filter(e -> !keys.contains(e.getExternalEntryId()))
                .toList();

        checkSelection(picked, plan, missing, errors);
        checkFinalState(eventId, plan, picked, missing, competitors, classes, errors, warnings);

        List<UnmappedClass> unmappedClasses = unmapped.entrySet().stream()
                .map(e -> new UnmappedClass(e.getKey(), e.getValue().className, e.getValue().classNumber, e.getValue().count))
                .toList();
        boolean blocked = !errors.isEmpty() || !unmappedClasses.isEmpty();
        boolean apply = !dryRun && !blocked;

        Map<Long, String> competitorNames = entryImports.competitorNames(missing);
        Instant now = Instant.now();
        List<CsvImportResult.Row> rows = new ArrayList<>();
        // Withdrawals first, so a replacement entry in the same class doesn't meet the old one in the
        // one-active-entry index.
        for (Entry e : missing) {
            boolean withdraw = apply && picked.withdraw().contains(e.getId());
            if (withdraw) {
                e.setStatus(EntryStatus.WITHDRAWN);
                e.setWithdrawnAt(now);
                entryRepository.save(e);
            }
            rows.add(new CsvImportResult.Row(Group.MISSING, null, null,
                    competitorNames.getOrDefault(e.getCompetitorId(), "entry " + e.getId()), null,
                    classes.name(e.getEventClassId()), null, e.getEventClassId(), e.getId(),
                    e.getTransponderNumberSnapshot(), e.getSecondaryTransponderNumber(), List.of(), Map.of(),
                    withdraw, null));
        }
        for (Planned p : plan) {
            Group group = p.group();
            boolean write = apply && (group == Group.NEW || group == Group.CHANGED && picked.update().contains(p.key));
            Long entryId = write ? save(eventId, p, competitors, now).getId()
                    : p.existing == null ? null : p.existing.getId();
            rows.add(new CsvImportResult.Row(group, p.key, p.row.line(), p.row.name(), p.row.brcaNumber(),
                    p.row.className(), p.row.classNumber(), p.eventClassId, entryId, primaryOf(p.row),
                    secondaryOf(p.row), p.changes, p.row.info(), write, null));
        }
        rows.addAll(skipped);

        Map<Group, Long> counts = rows.stream().collect(Collectors.groupingBy(CsvImportResult.Row::group, Collectors.counting()));
        int created = applied(rows, Group.NEW);
        int updated = applied(rows, Group.CHANGED);
        int withdrawn = applied(rows, Group.MISSING);
        var summary = new CsvImportResult.Summary(count(counts, Group.NEW), count(counts, Group.CHANGED),
                count(counts, Group.UNCHANGED), count(counts, Group.MISSING), count(counts, Group.SKIPPED),
                created, updated, withdrawn);
        if (apply && created + updated + withdrawn > 0) {
            recordImport(actor, event, summary, rows);
        }
        return new CsvImportResult(dryRun, blocked, apply, summary, unmappedClasses, errors, warnings, rows);
    }

    /** One audit row per import that changed entries: the counts, and each entry created, updated, withdrawn. */
    private void recordImport(Actor actor, Event event, CsvImportResult.Summary summary,
                              List<CsvImportResult.Row> rows) {
        List<Map<String, Object>> changed = new ArrayList<>();
        for (CsvImportResult.Row r : rows) {
            if (r.applied()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("group", r.group());
                m.put("name", r.name());
                m.put("className", r.className());
                m.put("entryId", r.entryId());
                changed.add(m);
            }
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("summary", summary);
        after.put("changed", changed);
        audit.entry(actor, "ENTRIES_IMPORTED").entity("event", event.getId()).event(event.getId())
                .summary("Imported entries into " + event.getName() + " from an RC-Timing CSV: " + summary.created()
                        + " new, " + summary.updated() + " updated, " + summary.withdrawn() + " withdrawn")
                .after(after).record();
    }

    /**
     * One planned entry per booked row. {@code update} rows go to {@code skipped}, a row booked twice
     * adds to {@code errors}, and each driver's competitor, if one exists, goes in {@code competitors}.
     */
    private List<Planned> plan(Long eventId, ParsedCsv parsed, Classes classes,
                               Map<String, UnmappedAccumulator> unmapped, Map<String, Competitor> competitors,
                               List<CsvImportResult.Row> skipped, List<String> errors) {
        Map<String, List<Competitor>> withoutBrcaNumber = competitorRepository.findWithoutBrcaNumberByMatchKey();
        Map<String, Integer> lineByKey = new HashMap<>();
        List<Planned> plan = new ArrayList<>();
        for (RcTimingCsvParser.Row row : parsed.rows()) {
            if (row.kind() == Kind.UPDATE) {
                skipped.add(new CsvImportResult.Row(Group.SKIPPED, null, row.line(), row.name(), row.brcaNumber(),
                        row.className(), row.classNumber(), null, null, text(row.primaryTransponder()),
                        text(row.secondaryTransponder()), List.of(), row.info(), false,
                        "Entry Desc is update, which only changes RC-Timing's member archive, so this row is not booked in"));
                continue;
            }
            Optional<Long> eventClassId = classes.resolve(row);
            if (eventClassId.isEmpty()) {
                unmapped.computeIfAbsent(classes.mappingKey(row), k -> new UnmappedAccumulator(row)).count++;
            }
            String key = entryKey(eventId, row, eventClassId.orElse(null), classes.mappingKey(row));
            Integer firstLine = lineByKey.putIfAbsent(key, row.line());
            if (firstLine != null) {
                errors.add("Line " + row.line() + " books " + row.name() + " into " + classLabel(row)
                        + " again (line " + firstLine + ")");
                continue;
            }
            String competitorKey = competitorKey(row);
            competitors.computeIfAbsent(competitorKey, k -> findCompetitor(k, row, withoutBrcaNumber));
            Entry existing = eventClassId.isEmpty() ? null
                    : entryRepository.findByExternalSourceAndExternalEntryId(ExternalSources.CSV, key).orElse(null);
            plan.add(new Planned(key, row, competitorKey, eventClassId.orElse(null), existing,
                    existing == null ? List.of() : changes(existing, row, classes)));
        }
        return plan;
    }

    /**
     * The entry's key, unique across events because the entries index is: the event, then the BRCA
     * number (or the name when it is 0), then the event class the row is placed in. A row that
     * can't be placed blocks the import, and its key, from the file's class, is never stored.
     */
    static String entryKey(Long eventId, RcTimingCsvParser.Row row, Long eventClassId, String mappingKey) {
        String classPart = eventClassId != null ? "class:" + eventClassId : "unplaced:" + mappingKey;
        return eventId + "/" + competitorKey(row) + "/" + classPart;
    }

    static String competitorKey(RcTimingCsvParser.Row row) {
        return row.brcaNumber() != null ? "brca:" + row.brcaNumber() : "name:" + storedKey(row.name());
    }

    /**
     * Finds the driver's competitor: one an earlier CSV import made, or else the one competitor from
     * any source with the same BRCA number, or, without one, the same name and no BRCA number.
     */
    private Competitor findCompetitor(String competitorKey, RcTimingCsvParser.Row row,
                                      Map<String, List<Competitor>> withoutBrcaNumber) {
        Optional<Competitor> fromCsv =
                competitorRepository.findByExternalSourceAndExternalId(ExternalSources.CSV, competitorKey);
        if (fromCsv.isPresent()) {
            return fromCsv.get();
        }
        List<Competitor> others = row.brcaNumber() != null
                ? competitorRepository.findByBrcaNumber(row.brcaNumber().toString())
                : withoutBrcaNumber.getOrDefault(Names.matchKey(row.name()), List.of());
        return others.size() == 1 ? others.get(0) : null;
    }

    private static List<Change> changes(Entry existing, RcTimingCsvParser.Row row, Classes classes) {
        List<Change> changes = new ArrayList<>();
        if (existing.getStatus() == EntryStatus.WITHDRAWN) {
            changes.add(new Change("Status", "Withdrawn", "Entered"));
        }
        // A BRCA-keyed row can rename a driver the CSV import made; a name-keyed row with another
        // name is another key, and a competitor from another source keeps its own name
        Competitor competitor = classes.competitor(existing.getCompetitorId());
        if (competitor != null && ExternalSources.CSV.equals(competitor.getExternalSource())
                && !competitor.getDisplayName().equals(row.name())) {
            changes.add(new Change("Name", competitor.getDisplayName(), row.name()));
        }
        if (!Objects.equals(blankToNull(existing.getTransponderNumberSnapshot()), blankToNull(primaryOf(row)))) {
            changes.add(new Change("Transponder", existing.getTransponderNumberSnapshot(), primaryOf(row)));
        }
        if (!Objects.equals(blankToNull(existing.getSecondaryTransponderNumber()), secondaryOf(row))) {
            changes.add(new Change("Second transponder", existing.getSecondaryTransponderNumber(), secondaryOf(row)));
        }
        return changes;
    }

    /** A pick that doesn't match this file means the preview is out of date, so nothing is applied. */
    private static void checkSelection(Selection picked, List<Planned> plan, List<Entry> missing, List<String> errors) {
        Set<String> changedKeys = plan.stream().filter(p -> p.group() == Group.CHANGED).map(p -> p.key).collect(Collectors.toSet());
        Set<Long> missingIds = missing.stream().map(Entry::getId).collect(Collectors.toSet());
        long staleUpdates = picked.update().stream().filter(k -> !changedKeys.contains(k)).count();
        long staleWithdrawals = picked.withdraw().stream().filter(id -> !missingIds.contains(id)).count();
        if (staleUpdates + staleWithdrawals > 0) {
            errors.add((staleUpdates + staleWithdrawals) + " of the picked entries no longer match this file or the "
                    + "event's entries. Preview the file again and pick again");
        }
    }

    /** Checks the event's entries as they would be after the import ({@link FinalState}). */
    private void checkFinalState(Long eventId, List<Planned> plan, Selection picked, List<Entry> missing,
                                 Map<String, Competitor> competitors, Classes classes,
                                 List<String> errors, List<String> warnings) {
        Set<Long> replaced = new HashSet<>();
        plan.stream().filter(p -> p.existing != null).forEach(p -> replaced.add(p.existing.getId()));
        missing.stream().filter(e -> picked.withdraw().contains(e.getId())).forEach(e -> replaced.add(e.getId()));

        FinalState state = entryImports.finalState(eventId, replaced, classes.eventClasses());
        for (Planned p : plan) {
            boolean fromFile = p.group() == Group.NEW || p.group() == Group.CHANGED && picked.update().contains(p.key);
            if (!fromFile && p.existing.getStatus() == EntryStatus.WITHDRAWN) {
                continue;
            }
            Competitor c = competitors.get(p.competitorKey);
            String driverKey = c == null ? "csv:" + p.competitorKey : "c:" + c.getId();
            if (fromFile) {
                state.add(driverKey, p.row.name(), p.eventClassId, primaryOf(p.row), secondaryOf(p.row));
            } else {
                state.add(driverKey, p.row.name(), p.existing.getEventClassId(),
                        p.existing.getTransponderNumberSnapshot(), p.existing.getSecondaryTransponderNumber());
            }
            if (p.group() == Group.NEW && primaryOf(p.row).isEmpty()) {
                warnings.add(p.row.name() + " (line " + p.row.line() + ") has no transponder");
            }
        }
        state.check(errors, warnings);
    }

    private Entry save(Long eventId, Planned p, Map<String, Competitor> competitors, Instant now) {
        Competitor competitor = competitors.get(p.competitorKey);
        if (competitor == null) {
            competitor = new Competitor();
            competitor.setExternalSource(ExternalSources.CSV);
            competitor.setExternalId(p.competitorKey);
        }
        boolean ours = ExternalSources.CSV.equals(competitor.getExternalSource());
        if (ours && (!p.row.name().equals(competitor.getDisplayName())
                || !Objects.equals(text(p.row.brcaNumber()), competitor.getBrcaNumber()))) {
            competitor.setDisplayName(p.row.name());
            competitor.setBrcaNumber(text(p.row.brcaNumber()));
            competitor = competitorRepository.save(competitor);
        }
        competitors.put(p.competitorKey, competitor);

        Entry e = p.existing;
        if (e == null) {
            e = new Entry();
            e.setEventId(eventId);
            e.setExternalSource(ExternalSources.CSV);
            e.setExternalEntryId(p.key);
            e.setSubmittedAt(now);
        }
        e.setCompetitorId(competitor.getId());
        e.setEventClassId(p.eventClassId);
        e.setTransponderNumberSnapshot(primaryOf(p.row));
        e.setSecondaryTransponderNumber(secondaryOf(p.row));
        if (e.getStatus() != EntryStatus.CONFIRMED) {
            e.setStatus(EntryStatus.CONFIRMED);
            e.setConfirmedAt(now);
            e.setWithdrawnAt(null);
        }
        return entryRepository.save(e);
    }

    /** The entry's primary number. With only {@code PT No 2} given, that becomes the primary. */
    private static String primaryOf(RcTimingCsvParser.Row row) {
        Long primary = row.primaryTransponder() != null ? row.primaryTransponder() : row.secondaryTransponder();
        return primary == null ? "" : primary.toString();
    }

    private static String secondaryOf(RcTimingCsvParser.Row row) {
        return row.primaryTransponder() == null ? null : text(row.secondaryTransponder());
    }

    private static String classLabel(RcTimingCsvParser.Row row) {
        return row.className() != null ? row.className() : "class " + row.classNumber();
    }

    private static int count(Map<Group, Long> counts, Group group) {
        return counts.getOrDefault(group, 0L).intValue();
    }

    private static int applied(List<CsvImportResult.Row> rows, Group group) {
        return (int) rows.stream().filter(r -> r.group() == group && r.applied()).count();
    }

    private static String text(Long number) {
        return number == null ? null : number.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * The form a name takes in the keys this import stores (competitor, entry and class mapping), so a later
     * import finds what an earlier one made. Stored, so it must not change; names are compared by
     * {@link Names#matchKey}.
     */
    static String storedKey(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record Planned(String key, RcTimingCsvParser.Row row, String competitorKey, Long eventClassId,
                           Entry existing, List<Change> changes) {
        Group group() {
            if (existing == null) {
                return Group.NEW;
            }
            return changes.isEmpty() ? Group.UNCHANGED : Group.CHANGED;
        }
    }

    private static final class UnmappedAccumulator {
        final String className;
        final Integer classNumber;
        int count;

        UnmappedAccumulator(RcTimingCsvParser.Row row) {
            this.className = row.className();
            this.classNumber = row.classNumber();
        }
    }

    /** The event's classes: placing a row, and naming a class or competitor in the preview. */
    private final class Classes {
        private final EventClasses eventClasses;
        private final Map<Long, Optional<Competitor>> competitorsById = new HashMap<>();

        Classes(EventClasses eventClasses) {
            this.eventClasses = eventClasses;
        }

        EventClasses eventClasses() {
            return eventClasses;
        }

        String mappingKey(RcTimingCsvParser.Row row) {
            String prefix = ExternalSources.CSV + ":";
            return row.className() != null ? prefix + storedKey(row.className()) : prefix + "#" + row.classNumber();
        }

        /**
         * A stored mapping first, then the one event class whose racing class has the file's class
         * name. Class Number places a row only when it has no class name, so a misspelt name is
         * never put in the wrong class: it is reported for mapping instead.
         */
        Optional<Long> resolve(RcTimingCsvParser.Row row) {
            Optional<Long> mapped = eventClasses.mapped(mappingKey(row));
            if (mapped.isPresent()) {
                return mapped;
            }
            return row.className() != null ? eventClasses.byName(row.className())
                    : eventClasses.atPosition(row.classNumber());
        }

        String name(Long eventClassId) {
            return eventClasses.name(eventClassId);
        }

        Competitor competitor(Long competitorId) {
            if (competitorId == null) {
                return null;
            }
            return competitorsById.computeIfAbsent(competitorId, competitorRepository::findById).orElse(null);
        }
    }
}
