package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.CreateEventRequest;
import dev.monkeypatch.rctiming.api.admin.dto.EventClassDto;
import dev.monkeypatch.rctiming.api.admin.dto.EventDetailDto;
import dev.monkeypatch.rctiming.api.admin.dto.EventDto;
import dev.monkeypatch.rctiming.api.admin.dto.GenerateRoundsRequest;
import dev.monkeypatch.rctiming.api.admin.dto.SeedFinalsRequest;
import dev.monkeypatch.rctiming.api.admin.dto.TransitionEventRequest;
import dev.monkeypatch.rctiming.api.admin.dto.UpdateEventRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.domain.format.EventClassService;
import dev.monkeypatch.rctiming.query.event.AdminEventListDto;
import dev.monkeypatch.rctiming.query.event.AdminEventQueryService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.EventRunOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Events under {@code /api/v1/admin/events}: the list and detail, creating and editing, moving an event through its
 * states, and generating the run order. The reads are open to ADMIN, RACE_DIRECTOR and REFEREE; creating and editing
 * is ADMIN only; moving the state, generating rounds and seeding finals are RACE_DIRECTOR or ADMIN. Changes are
 * recorded in the audit log. A missing event is a 404.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class EventController {

    private final EventService eventService;
    private final EventClassService eventClassService;
    private final AdminEventQueryService adminEventQueryService;
    private final EventRunOrderService runOrderService;

    public EventController(EventService eventService,
                           EventClassService eventClassService,
                           AdminEventQueryService adminEventQueryService,
                           EventRunOrderService runOrderService) {
        this.eventService = eventService;
        this.eventClassService = eventClassService;
        this.adminEventQueryService = adminEventQueryService;
        this.runOrderService = runOrderService;
    }

    /** Every event for the admin list. {@code GET}; read only. */
    @GetMapping
    public List<AdminEventListDto> listEvents() {
        return adminEventQueryService.listEvents();
    }

    /** One event with its classes. {@code GET /{id}}; 404 if it does not exist. */
    @GetMapping("/{id}")
    public EventDetailDto getEvent(@PathVariable Long id) {
        return EventDetailDto.from(
            eventService.findByIdOrThrow(id),
            eventClassService.listClassesForEvent(id).stream().map(EventClassDto::from).toList()
        );
    }

    /** Creates an event in DRAFT. {@code POST}; ADMIN; answers 201. Audited. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto createEvent(@RequestBody @Valid CreateEventRequest request) {
        return EventDto.from(eventService.create(CurrentOfficial.actor(),
                request.name(), request.eventDate(), request.trackId()));
    }

    /**
     * Changes the event's name, date and track. {@code PUT /{id}}; ADMIN. 409 unless the event is still in DRAFT.
     * Audited.
     */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventDto updateEvent(@PathVariable Long id,
                                 @RequestBody @Valid UpdateEventRequest request) {
        return EventDto.from(eventService.update(CurrentOfficial.actor(), id,
                request.name(), request.eventDate(), request.trackId()));
    }

    /**
     * Moves the event to the next state: DRAFT, PUBLISHED, OPEN, ENTRIES_CLOSED, IN_PROGRESS, COMPLETED (a published
     * event can also go back to DRAFT). {@code POST /{id}/transition}; RACE_DIRECTOR or ADMIN. 409 for a move the
     * event's state machine does not allow. Completing an event starts a backup and a results export. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public EventDto transitionEvent(@PathVariable Long id,
                                     @RequestBody @Valid TransitionEventRequest request) {
        return EventDto.from(eventService.transition(CurrentOfficial.actor(), id, request.targetStatus()));
    }

    /**
     * Generates the event's practice and qualifying rounds and finals from its entries and classes.
     * {@code POST /{id}/generate-rounds}; RACE_DIRECTOR or ADMIN; 204. 400 for a bad body. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/generate-rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> generateRounds(@PathVariable Long id,
                                               @Valid @RequestBody GenerateRoundsRequest req) {
        runOrderService.generateRounds(CurrentOfficial.actor(), req.toServiceRequest(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * Replaces the run order with a new one while no race has started, such as after entries or heat sizes change.
     * {@code POST /{id}/regenerate-rounds}; RACE_DIRECTOR or ADMIN; 204. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/regenerate-rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> regenerateRounds(@PathVariable Long id,
                                                 @Valid @RequestBody GenerateRoundsRequest req) {
        runOrderService.regenerateRounds(CurrentOfficial.actor(), req.toServiceRequest(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * Seeds a class's finals from its stored qualifying results. {@code POST /{id}/seed-finals}; RACE_DIRECTOR or
     * ADMIN; 204. Refused while no qualifying heat has finished and once a final has started. Race control has a
     * separate re-seed endpoint ({@code RaceControlController}). Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/seed-finals")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> seedFinals(@PathVariable Long id,
                                           @Valid @RequestBody SeedFinalsRequest req) {
        runOrderService.seedFinals(CurrentOfficial.actor(), id, req.eventClassId(),
                req.finalsCount(), req.carsPerFinal(), req.bumpCount());
        return ResponseEntity.noContent().build();
    }
}
