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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping
    public List<AdminEventListDto> listEvents() {
        return adminEventQueryService.listEvents();
    }

    @GetMapping("/{id}")
    public EventDetailDto getEvent(@PathVariable Long id) {
        return EventDetailDto.from(
            eventService.findByIdOrThrow(id),
            eventClassService.listClassesForEvent(id).stream().map(EventClassDto::from).toList()
        );
    }

    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto createEvent(Authentication auth, @RequestBody @Valid CreateEventRequest request) {
        return EventDto.from(eventService.create(CurrentOfficial.actor(auth),
                request.name(), request.eventDate(), request.trackId()));
    }

    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventDto updateEvent(Authentication auth, @PathVariable Long id,
                                 @RequestBody @Valid UpdateEventRequest request) {
        return EventDto.from(eventService.update(CurrentOfficial.actor(auth), id,
                request.name(), request.eventDate(), request.trackId()));
    }

    @Audited("audit_log")
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public EventDto transitionEvent(Authentication auth, @PathVariable Long id,
                                     @RequestBody @Valid TransitionEventRequest request) {
        return EventDto.from(eventService.transition(CurrentOfficial.actor(auth), id, request.targetStatus()));
    }

    @Audited("audit_log")
    @PostMapping("/{id}/generate-rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> generateRounds(Authentication auth, @PathVariable Long id,
                                               @Valid @RequestBody GenerateRoundsRequest req) {
        runOrderService.generateRounds(CurrentOfficial.actor(auth), req.toServiceRequest(id));
        return ResponseEntity.noContent().build();
    }

    @Audited("audit_log")
    @PostMapping("/{id}/regenerate-rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> regenerateRounds(Authentication auth, @PathVariable Long id,
                                                 @Valid @RequestBody GenerateRoundsRequest req) {
        runOrderService.regenerateRounds(CurrentOfficial.actor(auth), req.toServiceRequest(id));
        return ResponseEntity.noContent().build();
    }

    @Audited("audit_log")
    @PostMapping("/{id}/seed-finals")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> seedFinals(Authentication auth, @PathVariable Long id,
                                           @Valid @RequestBody SeedFinalsRequest req) {
        runOrderService.seedFinals(CurrentOfficial.actor(auth), id, req.eventClassId(),
                req.finalsCount(), req.carsPerFinal(), req.bumpCount());
        return ResponseEntity.noContent().build();
    }
}
