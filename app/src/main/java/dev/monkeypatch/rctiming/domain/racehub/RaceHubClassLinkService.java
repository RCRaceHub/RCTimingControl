package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository.EventClassRef;
import dev.monkeypatch.rctiming.domain.format.EventClassService;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Settles the classes an import could not place: for each class in a file it links the club's racing class (an
 * existing one, or a new one created from the file's name), puts that class in the event using the format it was last
 * entered with, and records the event's mapping. The link is club-wide ({@link RaceHubClassLink}), so every later
 * meeting, from the picker or a file, places that class without being asked again.
 *
 * <p>Called from {@code RaceHubImportController} on a Tomcat thread by an ADMIN. Each call is one write transaction.
 * Nothing here runs during an import itself, so a dry run still saves nothing.
 */
@Service
public class RaceHubClassLinkService {

    private final RaceHubClassLinkRepository linkRepository;
    private final RaceHubClassMappingRepository mappingRepository;
    private final EventClassRepository eventClassRepository;
    private final EventClassService eventClassService;
    private final RacingClassService racingClassService;
    private final EventRepository eventRepository;
    private final AuditService audit;

    public RaceHubClassLinkService(RaceHubClassLinkRepository linkRepository,
                                   RaceHubClassMappingRepository mappingRepository,
                                   EventClassRepository eventClassRepository,
                                   EventClassService eventClassService,
                                   RacingClassService racingClassService,
                                   EventRepository eventRepository,
                                   AuditService audit) {
        this.linkRepository = linkRepository;
        this.mappingRepository = mappingRepository;
        this.eventClassRepository = eventClassRepository;
        this.eventClassService = eventClassService;
        this.racingClassService = racingClassService;
        this.eventRepository = eventRepository;
        this.audit = audit;
    }

    /**
     * One class to settle. Give {@code racingClassId} to use an existing class or {@code createName} to make a new
     * one, never both. {@code templateId} is the format for a class not yet in the event; leave it null to use the
     * format the class was last entered with.
     */
    public record Resolution(String racehubEventClassId, Long racingClassId, String createName, Long templateId) {
    }

    /** What happened to one class: the racing class and event class it now lands in, and whether the class is new. */
    public record Resolved(String racehubEventClassId, Long racingClassId, Long eventClassId, boolean racingClassCreated) {
    }

    /** Every club-wide link, ordered by class key. */
    @ReadTransaction
    public List<RaceHubClassLink> list() {
        return linkRepository.findAllOrderByClassKey();
    }

    /**
     * Settles each class in {@code resolutions} for the event, all or none, and records
     * {@code RACEHUB_CLASS_LINKED} for each.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if the event or a racing class does not exist
     *         (HTTP 404)
     * @throws IllegalArgumentException if a key is blank, a resolution gives both or neither of a class and a name,
     *         a new name matches an existing class, or a class not yet in the event has no format to use (HTTP 400)
     */
    @Transactional
    public List<Resolved> resolve(Actor actor, Long eventId, List<Resolution> resolutions) {
        eventRepository.requireExists(eventId);
        List<Resolved> results = new ArrayList<>();
        for (Resolution r : resolutions) {
            results.add(resolveOne(actor, eventId, r));
        }
        return results;
    }

    private Resolved resolveOne(Actor actor, Long eventId, Resolution r) {
        String key = r.racehubEventClassId();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("RaceHub event_class_id is required");
        }
        boolean hasName = r.createName() != null && !r.createName().isBlank();
        if ((r.racingClassId() == null) == !hasName) {
            throw new IllegalArgumentException("Give either an existing class or a name for a new one for " + key);
        }
        RacingClass racingClass;
        boolean created = false;
        if (hasName) {
            String name = r.createName().strip();
            boolean taken = racingClassService.findAll().stream()
                    .anyMatch(c -> Names.matchKey(c.getName()).equals(Names.matchKey(name)));
            if (taken) {
                throw new IllegalArgumentException("A class called " + name + " already exists: choose it instead");
            }
            racingClass = racingClassService.create(actor, name, null, null);
            created = true;
        } else {
            racingClass = racingClassService.findById(r.racingClassId());
        }

        Long eventClassId = eventClassRepository.findRefsByEventId(eventId).stream()
                .filter(ec -> racingClass.getId().equals(ec.getRacingClassId()))
                .map(EventClassRef::getId).findFirst().orElse(null);
        if (eventClassId == null) {
            Long templateId = r.templateId() != null ? r.templateId()
                    : eventClassRepository.findLastTemplateId(racingClass.getId()).orElse(null);
            if (templateId == null) {
                throw new IllegalArgumentException(racingClass.getName()
                        + " has not been entered in an event before: choose a format for it");
            }
            eventClassId = eventClassService.addClassToEvent(actor, eventId, racingClass.getId(), templateId).getId();
        }

        RaceHubClassMapping mapping = mappingRepository.findByEventIdOrderByRacehubEventClassId(eventId).stream()
                .filter(m -> key.equals(m.getRacehubEventClassId())).findFirst().orElseGet(() -> {
                    RaceHubClassMapping m = new RaceHubClassMapping();
                    m.setEventId(eventId);
                    m.setRacehubEventClassId(key);
                    return m;
                });
        mapping.setEventClassId(eventClassId);
        mappingRepository.save(mapping);

        Optional<RaceHubClassLink> existing = linkRepository.findByClassKey(key);
        RaceHubClassLink link = existing.orElseGet(() -> {
            RaceHubClassLink l = new RaceHubClassLink();
            l.setClassKey(key);
            return l;
        });
        link.setRacingClassId(racingClass.getId());
        linkRepository.save(link);

        audit.entry(actor, "RACEHUB_CLASS_LINKED").entity("racing_class", racingClass.getId()).event(eventId)
                .summary("Linked the import class " + key + " to " + racingClass.getName()
                        + (created ? " (new class)" : ""))
                .record();
        return new Resolved(key, racingClass.getId(), eventClassId, created);
    }
}
