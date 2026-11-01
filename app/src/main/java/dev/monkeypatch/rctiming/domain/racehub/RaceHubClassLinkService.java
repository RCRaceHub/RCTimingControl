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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Links the club's RaceHub classes to its racing classes, once for the whole club ({@link RaceHubClassLink}, keyed by
 * the club class id). {@link #link} does only that, for the RaceHub classes screen. {@link #resolve} also puts the
 * racing class in an event, using the format it was last entered with, for an import that was held on an unlinked
 * class. After a link, every later meeting, from the picker, the feed or a file, places that class without being asked
 * again.
 *
 * <p>Called from the admin controllers on a Tomcat thread by an ADMIN. Each call is one write transaction.
 * Nothing here runs during an import itself, so a dry run still saves nothing.
 */
@Service
public class RaceHubClassLinkService {

    private final RaceHubClassLinkRepository linkRepository;
    private final EventClassRepository eventClassRepository;
    private final EventClassService eventClassService;
    private final RacingClassService racingClassService;
    private final EventRepository eventRepository;
    private final AuditService audit;

    public RaceHubClassLinkService(RaceHubClassLinkRepository linkRepository,
                                   EventClassRepository eventClassRepository,
                                   EventClassService eventClassService,
                                   RacingClassService racingClassService,
                                   EventRepository eventRepository,
                                   AuditService audit) {
        this.linkRepository = linkRepository;
        this.eventClassRepository = eventClassRepository;
        this.eventClassService = eventClassService;
        this.racingClassService = racingClassService;
        this.eventRepository = eventRepository;
        this.audit = audit;
    }

    /**
     * One class to link. Give {@code racingClassId} to use an existing class or {@code createName} to make a new
     * one, never both. {@code templateId} is the format for a class not yet in the event; leave it null to use the
     * format the class was last entered with.
     */
    public record Resolution(String classKey, Long racingClassId, String createName, Long templateId) {
    }

    /** What happened to one class: the racing class and event class it now lands in, and whether the class is new. */
    public record Resolved(String classKey, Long racingClassId, Long eventClassId, boolean racingClassCreated) {
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
        if (resolutions == null || resolutions.isEmpty()) {
            throw new IllegalArgumentException("Give at least one class to settle");
        }
        eventRepository.requireExists(eventId);
        List<Resolved> results = new ArrayList<>();
        for (Resolution r : resolutions) {
            results.add(resolveOne(actor, eventId, r));
        }
        return results;
    }

    /**
     * Links each class in {@code resolutions} to a racing class for the whole club, without touching any event, and
     * records {@code RACEHUB_CLASS_LINKED} for each. Used to seed a newly attached instance from RaceHub's class list.
     * A class linked again moves to the racing class now given.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if a racing class does not exist (HTTP 404)
     * @throws IllegalArgumentException if a key is blank, a resolution gives both or neither of a class and a name, or
     *         a new name matches an existing class (HTTP 400)
     */
    @Transactional
    public List<Resolved> link(Actor actor, List<Resolution> resolutions) {
        if (resolutions == null || resolutions.isEmpty()) {
            throw new IllegalArgumentException("Give at least one class to link");
        }
        List<Resolved> results = new ArrayList<>();
        for (Resolution r : resolutions) {
            LinkedClass linked = linkOne(actor, r);
            results.add(new Resolved(r.classKey(), linked.racingClass().getId(), null, linked.created()));
        }
        return results;
    }

    private record LinkedClass(RacingClass racingClass, boolean created) {
    }

    /** Makes or finds the racing class and stores the club's link to it. */
    private LinkedClass linkOne(Actor actor, Resolution r) {
        String key = r.classKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A club class id is required");
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
        RaceHubClassLink link = linkRepository.findByClassKey(key).orElseGet(() -> {
            RaceHubClassLink l = new RaceHubClassLink();
            l.setClassKey(key);
            return l;
        });
        link.setRacingClassId(racingClass.getId());
        linkRepository.save(link);
        audit.entry(actor, "RACEHUB_CLASS_LINKED").entity("racing_class", racingClass.getId())
                .summary("Linked the club class " + key + " to " + racingClass.getName()
                        + (created ? " (new class)" : ""))
                .record();
        return new LinkedClass(racingClass, created);
    }

    private Resolved resolveOne(Actor actor, Long eventId, Resolution r) {
        LinkedClass linked = linkOne(actor, r);
        RacingClass racingClass = linked.racingClass();
        String key = r.classKey();
        List<Long> inEvent = eventClassRepository.findRefsByEventId(eventId).stream()
                .filter(ec -> racingClass.getId().equals(ec.getRacingClassId()))
                .map(EventClassRef::getId).toList();
        if (inEvent.size() > 1) {
            throw new IllegalArgumentException(racingClass.getName() + " is in this event more than once: "
                    + "remove the extra one so " + key + " has a single class to land in");
        }
        Long eventClassId = inEvent.isEmpty() ? null : inEvent.get(0);
        if (eventClassId == null) {
            Long templateId = r.templateId() != null ? r.templateId()
                    : eventClassRepository.findLastTemplateId(racingClass.getId()).orElse(null);
            if (templateId == null) {
                throw new IllegalArgumentException(racingClass.getName()
                        + " has not been entered in an event before: choose a format for it");
            }
            eventClassId = eventClassService.addClassToEvent(actor, eventId, racingClass.getId(), templateId).getId();
        }

        return new Resolved(key, racingClass.getId(), eventClassId, linked.created());
    }
}
