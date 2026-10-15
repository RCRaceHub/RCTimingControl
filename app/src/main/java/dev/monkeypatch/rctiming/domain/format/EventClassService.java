package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public EventClassService(EventClassRepository eventClassRepository,
                             EventRepository eventRepository,
                             RaceFormatTemplateRepository templateRepository,
                             RacingClassRepository racingClassRepository,
                             RaceFormatService raceFormatService,
                             ObjectMapper objectMapper,
                             AuditService audit) {
        this.eventClassRepository = eventClassRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceFormatService = raceFormatService;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EventClass> listClassesForEvent(Long eventId) {
        eventRepository.requireExists(eventId);
        return eventClassRepository.findByEventId(eventId);
    }

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
