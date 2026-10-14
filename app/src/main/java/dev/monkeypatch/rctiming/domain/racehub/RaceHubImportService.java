package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.ExternalSources;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.checkin.TransponderSlot;
import dev.monkeypatch.rctiming.domain.checkin.TransponderSwapService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.entryimport.EntryImports;
import dev.monkeypatch.rctiming.domain.entryimport.EventClasses;
import dev.monkeypatch.rctiming.domain.entryimport.FinalState;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository.EventClassRef;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport.ExportEntry;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult.Action;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult.Row;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult.UnmappedClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Imports RaceHub Entry Export v1 into an event (L7, #15).
 *
 * <p>The import first builds a plan (one action per export entry), then applies it unless it is a
 * dry run or something blocks it. Entries are upserted by RaceHub's {@code entry_id}, and a row
 * changes an entry only when its {@code entry_version} is higher than the one already applied,
 * so replaying an export is a no-op. A withdrawn entry is marked WITHDRAWN and never deleted.
 *
 * <p>Another booking system can send the same format with its own {@code source} (#41). Its entries,
 * competitors and class mappings are keyed by that source, so its ids never meet RaceHub's, and it never
 * links the event to a RaceHub event, so no results are sent to RaceHub for it.
 */
@Service
public class RaceHubImportService {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** As in the published schema: it fits {@code external_source} and reads as a code, not free text. */
    private static final Pattern SOURCE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");

    private static final Set<String> ENTRY_STATUSES = Set.of("CONFIRMED", "WITHDRAWN");
    private static final Set<String> RACE_DAY_STATUSES = Set.of("NOT_ARRIVED", "ARRIVED");

    private final EventRepository eventRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceHubClassMappingRepository mappingRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final TransponderSwapService transponderSwapService;
    private final EntryImports entryImports;
    private final AuditService audit;

    public RaceHubImportService(EventRepository eventRepository,
                                EventClassRepository eventClassRepository,
                                RaceHubClassMappingRepository mappingRepository,
                                EntryRepository entryRepository,
                                CompetitorRepository competitorRepository,
                                TransponderSwapService transponderSwapService,
                                EntryImports entryImports,
                                AuditService audit) {
        this.eventRepository = eventRepository;
        this.eventClassRepository = eventClassRepository;
        this.mappingRepository = mappingRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.transponderSwapService = transponderSwapService;
        this.entryImports = entryImports;
        this.audit = audit;
    }

    /** What importing the file would do, saving nothing. */
    @Transactional
    public RaceHubImportResult preview(Long eventId, RaceHubEntryExport export) {
        return importEntries(null, eventId, export, true);
    }

    /**
     * Imports the file, or with {@code dryRun} only previews it. {@code actor} is who is importing (an official,
     * or the system for the automatic fetch); it is not needed, and may be null, for a dry run.
     */
    @Transactional
    public RaceHubImportResult importEntries(Actor actor, Long eventId, RaceHubEntryExport export, boolean dryRun) {
        Event event = eventRepository.getOrThrow(eventId);
        if (export == null || !Objects.equals(export.schemaVersion(), SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("Unsupported RaceHub export schema_version: expected "
                    + SUPPORTED_SCHEMA_VERSION + ", got " + (export == null ? null : export.schemaVersion()));
        }
        if (export.revision() == null) {
            throw new IllegalArgumentException("RaceHub export has no revision");
        }
        String source = sourceOf(export);
        boolean fromRaceHub = ExternalSources.RACEHUB.equals(source);
        String racehubEventId = !fromRaceHub || export.event() == null ? null : blankToNull(export.event().id());

        List<String> errors = new ArrayList<>();
        // Another system's event id means nothing to RaceHub, so only a RaceHub file links the event
        if (fromRaceHub) {
            checkRaceHubEvent(event, racehubEventId, errors);
        }
        List<String> warnings = new ArrayList<>();
        EventClasses eventClasses = entryImports.eventClasses(eventId);
        Map<String, UnmappedAccumulator> unmapped = new TreeMap<>();
        List<Planned> plan = plan(eventId, export, source, eventClasses, unmapped, errors, warnings);
        Map<String, Competitor> competitors = loadCompetitors(plan, source);
        checkFinalState(eventId, plan, competitors, eventClasses, errors, warnings);

        List<UnmappedClass> unmappedClasses = unmapped.entrySet().stream()
                .map(e -> new UnmappedClass(e.getKey(), e.getValue().rcClassName, e.getValue().className, e.getValue().count))
                .toList();
        boolean blocked = !errors.isEmpty() || !unmappedClasses.isEmpty();
        boolean apply = !dryRun && !blocked;

        Map<Planned, Long> savedIds = new IdentityHashMap<>();
        if (apply) {
            // Withdrawals first, so a driver's replacement entry in the same class does not meet
            // the old one in the one-active-entry index.
            plan.stream().filter(p -> p.action == Action.WITHDRAW)
                    .forEach(p -> savedIds.put(p, applyRow(eventId, p, competitors, source)));
            plan.stream().filter(p -> p.action != Action.WITHDRAW)
                    .forEach(p -> savedIds.put(p, applyRow(eventId, p, competitors, source)));
            if (fromRaceHub) {
                event.setRacehubLastImportAt(Instant.now());
                event.setRacehubLastRevision(export.revision());
                event.setRacehubEventId(racehubEventId);
                eventRepository.save(event);
            }
        }

        List<Row> rows = new ArrayList<>();
        int[] counts = new int[Action.values().length];
        for (Planned p : plan) {
            Long rctcEntryId = apply ? savedIds.get(p) : (p.existing == null ? null : p.existing.getId());
            counts[p.action.ordinal()]++;
            rows.add(new Row(p.row.entryId(), p.row.entryVersion(), p.row.driverDisplayName(),
                    p.action, p.eventClassId, rctcEntryId));
        }

        var summary = new RaceHubImportResult.Summary(
                counts[Action.CREATE.ordinal()], counts[Action.UPDATE.ordinal()],
                counts[Action.WITHDRAW.ordinal()], counts[Action.UNCHANGED.ordinal()],
                counts[Action.STALE.ordinal()], counts[Action.SKIP.ordinal()]);
        if (apply && (summary.created() + summary.updated() + summary.withdrawn()) > 0) {
            recordImport(Objects.requireNonNull(actor, "an import that saves needs an actor"), event, source,
                    export.revision(), summary, rows);
        }
        return new RaceHubImportResult(dryRun, blocked, apply,
                export.event() == null ? null : export.event().name(), export.revision(),
                summary, unmappedClasses, errors, warnings, rows);
    }

    /** A RaceHub file must name its event, and one event takes one RaceHub event's entries. */
    private static void checkRaceHubEvent(Event event, String racehubEventId, List<String> errors) {
        if (racehubEventId == null) {
            // Results go back to RaceHub under this id (#27), so an import without it would never be sent
            errors.add("This file has no RaceHub event id, so the results couldn't be sent back to RaceHub. "
                    + "Download the entry export from RaceHub again.");
        } else if (event.getRacehubEventId() != null && !event.getRacehubEventId().equals(racehubEventId)) {
            // Results go back to the RaceHub event recorded here (#27), so one event takes one RaceHub event's entries
            errors.add("This file is for RaceHub event " + racehubEventId + ", but this event's entries came from "
                    + "RaceHub event " + event.getRacehubEventId());
        }
    }

    /** One audit row per import that changed entries: the counts, and each entry created, updated or withdrawn. */
    private void recordImport(Actor actor, Event event, String source, Long revision,
                              RaceHubImportResult.Summary summary, List<Row> rows) {
        List<Map<String, Object>> changed = new ArrayList<>();
        for (Row r : rows) {
            if (r.action() == Action.CREATE || r.action() == Action.UPDATE || r.action() == Action.WITHDRAW) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("action", r.action());
                m.put("name", r.driverDisplayName());
                m.put("entryId", r.rctcEntryId());
                m.put("eventClassId", r.eventClassId());
                changed.add(m);
            }
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("source", source);
        after.put("revision", revision);
        after.put("summary", summary);
        after.put("changed", changed);
        audit.entry(actor, "ENTRIES_IMPORTED").entity("event", event.getId()).event(event.getId())
                .summary("Imported entries into " + event.getName() + " from "
                        + (ExternalSources.RACEHUB.equals(source) ? "RaceHub" : source) + " revision " + revision + ": "
                        + summary.created() + " new, " + summary.updated() + " updated, "
                        + summary.withdrawn() + " withdrawn")
                .after(after).record();
    }

    @Transactional(readOnly = true)
    public List<RaceHubClassMapping> listMappings(Long eventId) {
        eventRepository.requireExists(eventId);
        return mappingRepository.findByEventIdOrderByRacehubEventClassId(eventId);
    }

    /** Replaces the event's class mappings with {@code mappings} (RaceHub event_class_id → event class id). */
    @Transactional
    public List<RaceHubClassMapping> replaceMappings(Actor actor, Long eventId, Map<String, Long> mappings) {
        Event event = eventRepository.getOrThrow(eventId);
        Map<String, Long> before = new TreeMap<>();
        mappingRepository.findByEventIdOrderByRacehubEventClassId(eventId)
                .forEach(m -> before.put(m.getRacehubEventClassId(), m.getEventClassId()));
        Set<Long> eventClassIds = eventClassRepository.findRefsByEventId(eventId).stream()
                .map(EventClassRef::getId).collect(Collectors.toSet());
        mappings.forEach((racehubId, eventClassId) -> {
            if (racehubId == null || racehubId.isBlank()) {
                throw new IllegalArgumentException("RaceHub event_class_id is required");
            }
            if (!eventClassIds.contains(eventClassId)) {
                throw new IllegalArgumentException("Event class " + eventClassId + " is not in this event");
            }
        });
        mappingRepository.deleteByEventId(eventId);
        List<RaceHubClassMapping> saved = new ArrayList<>();
        new TreeMap<>(mappings).forEach((racehubId, eventClassId) -> {
            RaceHubClassMapping m = new RaceHubClassMapping();
            m.setEventId(eventId);
            m.setRacehubEventClassId(racehubId);
            m.setEventClassId(eventClassId);
            saved.add(mappingRepository.save(m));
        });
        audit.entry(actor, "CLASS_MAPPINGS_REPLACED").entity("event", eventId).event(eventId)
                .summary("Changed the import class mappings of " + event.getName() + " (" + before.size()
                        + " before, " + mappings.size() + " after)")
                .before(before).after(new TreeMap<>(mappings)).record();
        return saved;
    }

    /** The file's {@code source}, RACEHUB when it has none. */
    static String sourceOf(RaceHubEntryExport export) {
        String source = export.source();
        if (source == null) {
            return ExternalSources.RACEHUB;
        }
        if (!SOURCE.matcher(source).matches()) {
            throw new IllegalArgumentException("source must be up to 30 capital letters, digits or underscores, "
                    + "starting with a letter, not \"" + source + "\"");
        }
        if (ExternalSources.CSV.equals(source)) {
            throw new IllegalArgumentException("source CSV is used by the RC-Timing CSV import; choose another");
        }
        return source;
    }

    private static List<String> validate(ExportEntry row, String label) {
        List<String> problems = new ArrayList<>();
        if (row.entryId() == null || row.entryId().isBlank()) {
            problems.add("An entry has no entry_id");
            return problems;
        }
        if (row.entryVersion() == null) {
            problems.add(label + " has no entry_version");
        }
        if (row.entryStatus() == null || !ENTRY_STATUSES.contains(row.entryStatus())) {
            problems.add(label + " has an unknown entry_status: " + row.entryStatus());
        }
        if (row.raceDayStatus() != null && !RACE_DAY_STATUSES.contains(row.raceDayStatus())) {
            problems.add(label + " has an unknown race_day_status: " + row.raceDayStatus());
        }
        if (row.driverProfileId() == null || row.driverProfileId().isBlank()) {
            problems.add(label + " has no driver_profile_id");
        }
        if (row.eventClassId() == null || row.eventClassId().isBlank()) {
            problems.add(label + " has no event_class_id");
        }
        return problems;
    }

    /** One action per valid export entry; a row that can't be planned adds to {@code errors} instead. */
    private List<Planned> plan(Long eventId, RaceHubEntryExport export, String source, EventClasses eventClasses,
                               Map<String, UnmappedAccumulator> unmapped, List<String> errors, List<String> warnings) {
        List<ExportEntry> exportEntries = export.entries() == null ? List.of() : export.entries();
        ClassResolver classes = new ClassResolver(source, eventClasses);
        List<Planned> plan = new ArrayList<>();
        Set<String> seenEntryIds = new HashSet<>();
        Map<Long, Set<TransponderSlot>> swapped = transponderSwapService.swappedSlots(eventId);

        for (ExportEntry row : exportEntries) {
            if (row == null) {
                errors.add("The export has an empty (null) entry");
                continue;
            }
            String label = row.entryId() == null ? "entry without entry_id" : "entry " + row.entryId();
            List<String> rowErrors = validate(row, label);
            if (rowErrors.isEmpty() && !seenEntryIds.add(row.entryId())) {
                rowErrors.add(label + " appears more than once in the export");
            }
            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                continue;
            }

            Entry existing = entryRepository
                    .findByExternalSourceAndExternalEntryId(source, row.entryId())
                    .orElse(null);
            if (existing != null && !existing.getEventId().equals(eventId)) {
                errors.add(label + " was imported into another event");
                continue;
            }
            Action action = action(row, existing);

            // A class is needed for every row that leaves an active entry behind.
            Long eventClassId = existing == null ? null : existing.getEventClassId();
            Transponders transponders = null;
            if (action == Action.CREATE || action == Action.UPDATE) {
                Optional<Long> resolved = classes.resolve(row);
                if (resolved.isPresent()) {
                    eventClassId = resolved.get();
                } else {
                    unmapped.computeIfAbsent(classes.mappingKey(row), k -> new UnmappedAccumulator(row)).count++;
                }
                transponders = transponders(row, existing,
                        existing == null ? Set.of() : swapped.getOrDefault(existing.getId(), Set.of()));
                String name = row.driverDisplayName() != null ? row.driverDisplayName() : label;
                warnings.addAll(transponders.differences(name));
            }
            plan.add(new Planned(row, action, existing, eventClassId, transponders));
        }
        return plan;
    }

    /** Upserts by entry id: a row changes an entry only when its entry_version is higher than the one applied. */
    private static Action action(ExportEntry row, Entry existing) {
        boolean withdrawn = "WITHDRAWN".equals(row.entryStatus());
        if (existing == null) {
            return withdrawn ? Action.SKIP : Action.CREATE;
        }
        long applied = existing.getExternalEntryVersion() == null ? Long.MIN_VALUE : existing.getExternalEntryVersion();
        if (row.entryVersion() < applied) {
            return Action.STALE;
        }
        if (row.entryVersion() == applied) {
            return Action.UNCHANGED;
        }
        return withdrawn ? Action.WITHDRAW : Action.UPDATE;
    }

    private Map<String, Competitor> loadCompetitors(List<Planned> plan, String source) {
        Map<String, Competitor> byProfile = new HashMap<>();
        for (Planned p : plan) {
            byProfile.computeIfAbsent(p.row.driverProfileId(), id ->
                    competitorRepository.findByExternalSourceAndExternalId(source, id).orElse(null));
        }
        return byProfile;
    }

    /** Checks the event's entries as they would be after the import ({@link FinalState}). */
    private void checkFinalState(Long eventId, List<Planned> plan, Map<String, Competitor> competitors,
                                 EventClasses classes, List<String> errors, List<String> warnings) {
        Set<Long> replaced = plan.stream().filter(p -> p.existing != null).map(p -> p.existing.getId())
                .collect(Collectors.toSet());
        FinalState state = entryImports.finalState(eventId, replaced, classes);
        for (Planned p : plan) {
            boolean activeAfter = switch (p.action) {
                case CREATE, UPDATE -> true;
                case UNCHANGED, STALE -> p.existing.getStatus() != EntryStatus.WITHDRAWN;
                case WITHDRAW, SKIP -> false;
            };
            if (!activeAfter) {
                continue;
            }
            boolean rowWins = p.action == Action.CREATE || p.action == Action.UPDATE;
            Competitor c = competitors.get(p.row.driverProfileId());
            String driverKey = c == null ? "rh:" + p.row.driverProfileId() : "c:" + c.getId();
            String name = p.row.driverDisplayName() != null ? p.row.driverDisplayName() : "entry " + p.row.entryId();
            String primary = rowWins ? p.transponders.primary() : p.existing.getTransponderNumberSnapshot();
            String secondary = rowWins ? p.transponders.secondary() : p.existing.getSecondaryTransponderNumber();
            state.add(driverKey, name, p.eventClassId, primary, secondary);

            if (rowWins && p.transponders.primary().isEmpty()) {
                warnings.add(name + " (entry " + p.row.entryId() + ") has no transponder");
            }
        }
        state.check(errors, warnings);
    }

    private Long applyRow(Long eventId, Planned p, Map<String, Competitor> competitors, String source) {
        ExportEntry row = p.row;
        Instant now = Instant.now();
        switch (p.action) {
            case UNCHANGED, STALE -> {
                return p.existing.getId();
            }
            case SKIP -> {
                return null;
            }
            case WITHDRAW -> {
                Entry e = p.existing;
                e.setStatus(EntryStatus.WITHDRAWN);
                e.setWithdrawnAt(now);
                e.setExternalEntryVersion(row.entryVersion());
                e.setRacehubArrival(row.raceDayStatus());
                return entryRepository.save(e).getId();
            }
            default -> {
                Competitor competitor = upsertCompetitor(row, competitors, source);
                Entry e = p.existing;
                if (e == null) {
                    e = new Entry();
                    e.setEventId(eventId);
                    e.setExternalSource(source);
                    e.setExternalEntryId(row.entryId());
                    e.setSubmittedAt(now);
                }
                e.setCompetitorId(competitor.getId());
                e.setEventClassId(p.eventClassId);
                // Results go back to RaceHub by its class ids (#27), so another system's are not kept
                e.setRacehubEventClassId(ExternalSources.RACEHUB.equals(source) ? row.eventClassId() : null);
                e.setTransponderNumberSnapshot(p.transponders.primary());
                e.setSecondaryTransponderNumber(p.transponders.secondary());
                e.setImportedTransponderNumber(p.transponders.importedPrimary());
                e.setImportedSecondaryTransponderNumber(p.transponders.importedSecondary());
                if (e.getStatus() != EntryStatus.CONFIRMED) {
                    e.setStatus(EntryStatus.CONFIRMED);
                    e.setConfirmedAt(now);
                    e.setWithdrawnAt(null);
                }
                e.setExternalEntryVersion(row.entryVersion());
                e.setRacehubArrival(row.raceDayStatus());
                return entryRepository.save(e).getId();
            }
        }
    }

    private Competitor upsertCompetitor(ExportEntry row, Map<String, Competitor> competitors, String source) {
        Competitor c = competitors.get(row.driverProfileId());
        if (c == null) {
            c = new Competitor();
            c.setExternalSource(source);
            c.setExternalId(row.driverProfileId());
        }
        String name = row.driverDisplayName();
        String unnamed = ExternalSources.RACEHUB.equals(source) ? "RaceHub driver " : "Driver ";
        c.setDisplayName(name == null || name.isBlank() ? unnamed + row.driverProfileId() : name.trim());
        c.setBrcaNumber(blankToNull(row.brcaNumber()));
        c.setHomeClub(blankToNull(row.homeClub()));
        c = competitorRepository.save(c);
        competitors.put(row.driverProfileId(), c);
        return c;
    }

    /** The entry's primary number. With only a secondary given, that becomes the primary. */
    private static String primaryOf(ExportEntry row) {
        String primary = blankToNull(row.primaryTransponder());
        if (primary != null) {
            return primary;
        }
        String secondary = blankToNull(row.secondaryTransponder());
        return secondary != null ? secondary : "";
    }

    private static String secondaryOf(ExportEntry row) {
        return blankToNull(row.primaryTransponder()) == null ? null : blankToNull(row.secondaryTransponder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record Planned(ExportEntry row, Action action, Entry existing, Long eventClassId, Transponders transponders) {}

    /**
     * The numbers an entry ends up with, and the file's numbers that a swap on the day overrides (#50).
     *
     * @param importedPrimary the file's primary, kept only when the primary was swapped on the day and differs
     * @param importedSecondary the same for the secondary
     */
    private record Transponders(String primary, String secondary, String importedPrimary, String importedSecondary) {

        List<String> differences(String name) {
            List<String> messages = new ArrayList<>();
            if (importedPrimary != null) {
                messages.add(name + " keeps transponder " + primary + " swapped on the day; the file has "
                        + importedPrimary);
            }
            if (importedSecondary != null) {
                messages.add(name + " keeps " + (secondary == null
                        ? "no secondary transponder (removed on the day)"
                        : "secondary transponder " + secondary + " swapped on the day")
                        + "; the file has " + importedSecondary);
            }
            return messages;
        }
    }

    /**
     * The file's numbers, except that a slot swapped on the day keeps the local number (#50). The swap stays and
     * the file's number is recorded as a difference, so nothing is blocked.
     */
    private static Transponders transponders(ExportEntry row, Entry existing, Set<TransponderSlot> swapped) {
        String filePrimary = primaryOf(row);
        String fileSecondary = secondaryOf(row);
        if (existing == null || swapped.isEmpty()) {
            return new Transponders(filePrimary, fileSecondary, null, null);
        }
        boolean keepPrimary = swapped.contains(TransponderSlot.PRIMARY);
        boolean keepSecondary = swapped.contains(TransponderSlot.SECONDARY);
        String primary = keepPrimary ? existing.getTransponderNumberSnapshot() : filePrimary;
        String secondary = keepSecondary ? existing.getSecondaryTransponderNumber() : fileSecondary;
        String importedPrimary = keepPrimary && !Objects.equals(primary, filePrimary) && !filePrimary.isEmpty()
                ? filePrimary : null;
        String importedSecondary = keepSecondary && !Objects.equals(secondary, fileSecondary) ? fileSecondary : null;
        // A kept number and a file number can't both sit on one entry
        if (secondary != null && secondary.equals(primary)) {
            secondary = null;
        }
        return new Transponders(primary, secondary, importedPrimary, importedSecondary);
    }

    private static final class UnmappedAccumulator {
        final String rcClassName;
        final String className;
        int count;

        UnmappedAccumulator(ExportEntry row) {
            this.rcClassName = row.rcClassName();
            this.className = row.className();
        }
    }

    /**
     * Finds the event class for a RaceHub class: a stored mapping first, then the one event class
     * whose racing class has the same name as {@code rc_class_name}. Another source's mappings are
     * keyed by that source and its class id, so they never meet RaceHub's.
     */
    private record ClassResolver(String source, EventClasses classes) {

        String mappingKey(ExportEntry row) {
            return ExternalSources.RACEHUB.equals(source) ? row.eventClassId() : source + ":" + row.eventClassId();
        }

        Optional<Long> resolve(ExportEntry row) {
            Optional<Long> mapped = classes.mapped(mappingKey(row));
            if (mapped.isPresent() || row.rcClassName() == null) {
                return mapped;
            }
            return classes.byName(row.rcClassName());
        }
    }
}
