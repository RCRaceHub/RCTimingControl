package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which classes race in an event and what format each runs to. Adding a class copies a template's config into the class
 * as its own snapshot (see {@link RaceFormatService#assignTemplateToEventClass}); every change is audit-logged. Runs in
 * a write transaction unless noted.
 */
@Service
@Transactional
public class EventClassService {

    private final EventClassRepository eventClassRepository;
    private final EventRepository eventRepository;
    private final RaceFormatTemplateRepository templateRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceFormatService raceFormatService;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final EntryRepository entryRepository;
    private final EntryService entryService;
    private final RaceRepository raceRepository;

    public EventClassService(EventClassRepository eventClassRepository,
                             EventRepository eventRepository,
                             RaceFormatTemplateRepository templateRepository,
                             RacingClassRepository racingClassRepository,
                             RaceFormatService raceFormatService,
                             ObjectMapper objectMapper,
                             AuditService audit,
                             EntryRepository entryRepository,
                             EntryService entryService,
                             RaceRepository raceRepository) {
        this.eventClassRepository = eventClassRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceFormatService = raceFormatService;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.entryRepository = entryRepository;
        this.entryService = entryService;
        this.raceRepository = raceRepository;
    }

    /** Every class entered in the event; a 404 if there is no such event. */
    @ReadTransaction
    public List<EventClass> listClassesForEvent(Long eventId) {
        eventRepository.requireExists(eventId);
        return eventClassRepository.findByEventId(eventId);
    }

    /**
     * Enters a racing class in the event using a format template, and records {@code EVENT_CLASS_ADDED}. The template
     * is copied, not referenced, so later edits to it do not change this class.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if the event, class or template does not exist
     */
    public EventClass addClassToEvent(Actor actor, Long eventId, Long racingClassId, Long templateId) {
        Event event = eventRepository.getOrThrow(eventId);
        RacingClass racingClass = racingClassRepository.getOrThrow(racingClassId);
        RaceFormatTemplate template = templateRepository.getOrThrow(templateId);

        EventClass ec = raceFormatService.assignTemplateToEventClass(template);
        ec.setEventId(eventId);
        ec.setRacingClassId(racingClassId);
        EventClass saved = eventClassRepository.save(ec);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("racingClassId", racingClass.getId());
        after.put("templateId", template.getId());
        after.put("templateName", template.getName());
        audit.entry(actor, "EVENT_CLASS_ADDED").entity("event_class", saved.getId()).event(eventId)
                .summary("Added " + racingClass.getName() + " to " + event.getName()
                        + " using the format " + template.getName())
                .after(after).record();
        return saved;
    }

    /**
     * Takes a class out of the event and records {@code EVENT_CLASS_REMOVED}. Refused while any race has been
     * generated for the class, since the races and their laps would be lost; the official clears the run order first.
     * A class with entries that are not withdrawn is refused too, unless {@code withdrawEntries} is set, when those
     * entries are withdrawn first (kept, never deleted, each with an {@code ADMIN_WITHDRAW} row in the entry audit
     * log) and are left with no class. The class's RaceHub class mappings go with it, so a re-import of that RaceHub
     * class reports it as unmapped. Races are the only guard; the event's status is not checked.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if the class does not exist
     * @throws IllegalArgumentException if the class belongs to another event
     * @throws StateConflictException if races exist for the class, or it has active entries and
     *         {@code withdrawEntries} is false (HTTP 409)
     */
    public void removeClassFromEvent(Actor actor, Long eventId, Long classId, boolean withdrawEntries) {
        EventClass ec = getEventClassOrThrow(classId);
        if (!eventId.equals(ec.getEventId())) {
            throw new IllegalArgumentException("EventClass " + classId + " does not belong to event " + eventId);
        }
        String name = describe(ec);
        if (raceRepository.existsByEventClassId(classId)) {
            throw new StateConflictException("Races have been generated for " + name
                    + ". Delete the event's run order before removing the class.");
        }
        List<Entry> active = entryRepository.findActiveByEventClassId(classId);
        if (!active.isEmpty() && !withdrawEntries) {
            throw new StateConflictException(name + " has " + active.size() + " active "
                    + (active.size() == 1 ? "entry" : "entries")
                    + ". Confirm to withdraw them and remove the class.");
        }
        for (Entry entry : active) {
            entryService.adminWithdraw(entry.getId(), actor.userId(), "Class " + name + " removed from the event");
        }
        eventClassRepository.deleteById(classId);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("racingClassId", ec.getRacingClassId());
        before.put("templateId", ec.getTemplateId());
        before.put("withdrawnEntries", active.size());
        audit.entry(actor, "EVENT_CLASS_REMOVED").entity("event_class", classId).event(eventId)
                .summary("Removed " + name + " from the event"
                        + (active.isEmpty() ? "" : " and withdrew " + active.size() + " entries"))
                .before(before).record();
    }

    /**
     * Sets or, with a null or empty map, clears the class's format override, and records {@code
     * EVENT_CLASS_OVERRIDES_CHANGED}. The override is checked against the class's format before it is saved, so it
     * cannot break race generation later.
     *
     * @throws IllegalArgumentException if the class belongs to another event or the override does not fit the format
     */
    public EventClass updateOverrides(Actor actor, Long eventId, Long classId, Map<String, Object> requested) {
        EventClass ec = getEventClassOrThrow(classId);
        if (!eventId.equals(ec.getEventId())) {
            throw new IllegalArgumentException("EventClass " + classId + " does not belong to event " + eventId);
        }
        Map<String, Object> before = ec.getConfigOverride();
        Map<String, Object> override = requested == null || requested.isEmpty() ? null : new HashMap<>(requested);
        ec.setConfigOverride(override);
        effectiveConfig(ec); // refuse an override that does not fit the format, before races are generated from it
        EventClass saved = eventClassRepository.save(ec);
        audit.entry(actor, "EVENT_CLASS_OVERRIDES_CHANGED").entity("event_class", classId).event(ec.getEventId())
                .summary((override == null ? "Cleared the format overrides of " : "Changed the format overrides of ")
                        + describe(ec))
                .before(before).after(override).record();
        return saved;
    }

    /**
     * EVENT-06: Assigns the same non-null combined_race_group to every supplied event class
     * so they race together but score separately.
     *
     * @throws IllegalArgumentException if fewer than two ids are given or one belongs to another event
     */
    public List<EventClass> combineClasses(Actor actor, Long eventId, List<Long> eventClassIds) {
        if (eventClassIds == null || eventClassIds.size() < 2) {
            throw new IllegalArgumentException("At least 2 event class ids required to combine");
        }
        // The current time in milliseconds is the shared group id. Two combines would collide only if they
        // landed in the same millisecond, which officials clicking a button do not manage.
        long groupId = Instant.now().toEpochMilli();

        List<EventClass> result = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Long id : eventClassIds) {
            EventClass ec = getEventClassOrThrow(id);
            if (ec.getEventId() == null || !ec.getEventId().equals(eventId)) {
                throw new IllegalArgumentException("EventClass " + id + " does not belong to event " + eventId);
            }
            ec.setCombinedRaceGroup(groupId);
            result.add(eventClassRepository.save(ec));
            names.add(describe(ec));
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("eventClassIds", eventClassIds);
        after.put("combinedRaceGroup", groupId);
        audit.entry(actor, "EVENT_CLASSES_COMBINED").entity("event", eventId).event(eventId)
                .summary("Combined " + String.join(", ", names) + " to race together and score separately")
                .after(after).record();
        return result;
    }

    /** The snapshot with the override laid over it; an override that does not fit the format is a bad request. */
    private RaceFormatConfig effectiveConfig(EventClass ec) {
        if (ec.getConfigOverride() == null || ec.getConfigOverride().isEmpty() || ec.getConfigSnapshot() == null) {
            return ec.getConfigSnapshot();
        }
        Map<String, Object> snapshotMap = objectMapper.convertValue(
                ec.getConfigSnapshot(), new TypeReference<Map<String, Object>>() {});
        snapshotMap.putAll(ec.getConfigOverride());
        try {
            return objectMapper.convertValue(snapshotMap, RaceFormatConfig.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The format overrides do not fit the class's format: "
                    + e.getMessage(), e);
        }
    }

    /** The class's name, for an audit summary. */
    private String describe(EventClass ec) {
        return racingClassRepository.findById(ec.getRacingClassId()).map(RacingClass::getName)
                .orElse("class " + ec.getRacingClassId());
    }

    private EventClass getEventClassOrThrow(Long id) {
        return eventClassRepository.getOrThrow(id);
    }
}
