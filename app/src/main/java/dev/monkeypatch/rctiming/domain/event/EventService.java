package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Race meetings: creating and editing one, moving it through its states ({@link EventStateMachineService}) and
 * turning its live feed on or off. Every change is audit-logged with the official who made it.
 */
@Service
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final EventStateMachineService stateMachineService;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public EventService(EventRepository eventRepository,
                        EventStateMachineService stateMachineService,
                        ApplicationEventPublisher events,
                        AuditService audit) {
        this.eventRepository = eventRepository;
        this.stateMachineService = stateMachineService;
        this.events = events;
        this.audit = audit;
    }

    /** Creates an event in DRAFT and records {@code EVENT_CREATED}. */
    public Event create(Actor actor, String name, LocalDate eventDate, Long trackId) {
        Event event = new Event();
        event.setName(name);
        event.setEventDate(eventDate);
        event.setTrackId(trackId);
        event.setStatus(EventStatus.DRAFT);
        Event saved = eventRepository.save(event);
        audit.entry(actor, "EVENT_CREATED").entity("event", saved.getId()).event(saved.getId())
                .summary("Created event " + saved.getName())
                .after(detailsOf(saved)).record();
        return saved;
    }

    /**
     * Changes an event's name, date and track and records {@code EVENT_UPDATED}.
     *
     * @throws IllegalStateTransitionException if the event is not in DRAFT (HTTP 409)
     */
    public Event update(Actor actor, Long id, String name, LocalDate eventDate, Long trackId) {
        Event event = getEventOrThrow(id);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new IllegalStateTransitionException(
                "Event details can only be updated while in DRAFT status");
        }
        Map<String, Object> before = detailsOf(event);
        event.setName(name);
        event.setEventDate(eventDate);
        event.setTrackId(trackId);
        Event saved = eventRepository.save(event);
        audit.entry(actor, "EVENT_UPDATED").entity("event", id).event(id)
                .summary("Changed the details of event " + saved.getName())
                .before(before).after(detailsOf(saved)).record();
        return saved;
    }

    /**
     * Moves the event to {@code targetStatus} and records {@code EVENT_<status>}. Moving to COMPLETED also publishes an
     * {@link EventCompleted}.
     *
     * @throws IllegalStateTransitionException if the state machine does not allow the move (HTTP 409)
     */
    public Event transition(Actor actor, Long id, EventStatus targetStatus) {
        Event event = getEventOrThrow(id);
        EventStatus from = event.getStatus();
        stateMachineService.transition(event, targetStatus);
        Event saved = eventRepository.save(event);
        boolean completed = targetStatus == EventStatus.COMPLETED;
        // Closing the day starts a backup and a results export that name nobody, so the reason is recorded here
        audit.entry(actor, "EVENT_" + targetStatus).entity("event", id).event(id)
                .summary("Moved event " + saved.getName() + " from " + from + " to " + targetStatus
                        + (completed ? ", which starts a backup and a results export" : ""))
                .before(from).after(targetStatus).record();
        if (completed) {
            events.publishEvent(new EventCompleted(event.getId()));
        }
        return saved;
    }

    /**
     * Turns the live feed (#28) on or off for the event's races, recording the switch only when it changes.
     *
     * @return whether the feed is now on
     */
    public boolean setLiveFeedEnabled(Actor actor, Long id, boolean enabled) {
        Event event = getEventOrThrow(id);
        boolean before = event.isLiveFeedEnabled();
        if (before == enabled) {
            return enabled;
        }
        event.setLiveFeedEnabled(enabled);
        eventRepository.save(event);
        audit.entry(actor, "LIVE_FEED_SWITCHED").entity("event", id).event(id)
                .summary("Turned the live feed " + (enabled ? "on" : "off") + " for " + event.getName())
                .before(before).after(enabled).record();
        return enabled;
    }

    /** Sets how auto run (#338) paces the event, recording the change only when something differs. */
    public Event setAutoRunSettings(Actor actor, Long id, int gapSeconds, boolean holdBeforeFinals,
                                 boolean autoStart) {
        Event event = getEventOrThrow(id);
        if (event.getAutoRunGapSeconds() == gapSeconds && event.isAutoRunHoldBeforeFinals() == holdBeforeFinals
                && event.isAutoRunAutoStart() == autoStart) {
            return event;
        }
        Map<String, Object> before = Map.of("gapSeconds", event.getAutoRunGapSeconds(),
                "holdBeforeFinals", event.isAutoRunHoldBeforeFinals(), "autoStart", event.isAutoRunAutoStart());
        event.setAutoRunGapSeconds(gapSeconds);
        event.setAutoRunHoldBeforeFinals(holdBeforeFinals);
        event.setAutoRunAutoStart(autoStart);
        Event saved = eventRepository.save(event);
        audit.entry(actor, "AUTO_RUN_SETTINGS_CHANGED").entity("event", id).event(id)
                .summary("Changed the auto run settings for " + event.getName())
                .before(before).after(Map.of("gapSeconds", gapSeconds, "holdBeforeFinals", holdBeforeFinals,
                        "autoStart", autoStart))
                .record();
        return saved;
    }

    /** The event, read in a read-only transaction. Throws a 404 if there is none. */
    @Transactional(readOnly = true)
    public Event findByIdOrThrow(Long id) {
        return getEventOrThrow(id);
    }

    private Event getEventOrThrow(Long id) {
        return eventRepository.getOrThrow(id);
    }

    private static Map<String, Object> detailsOf(Event e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getName());
        m.put("eventDate", e.getEventDate() == null ? null : e.getEventDate().toString());
        m.put("trackId", e.getTrackId());
        return m;
    }
}
