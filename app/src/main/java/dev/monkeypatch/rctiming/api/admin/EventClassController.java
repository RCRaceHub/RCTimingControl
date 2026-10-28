package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.AddEventClassRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CombineClassesRequest;
import dev.monkeypatch.rctiming.api.admin.dto.EventClassDto;
import dev.monkeypatch.rctiming.api.admin.dto.UpdateEventClassOverrideRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.format.EventClassService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * An event's classes under {@code /api/v1/admin/events/{eventId}/classes}: adding a racing class with a format,
 * removing one, overriding the format for the event, and combining classes to race together. The class is open to
 * ADMIN, RACE_DIRECTOR and REFEREE but every endpoint is narrowed to ADMIN. Changes are audited.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/classes")
@RaceControlStaff
public class EventClassController {

    private final EventClassService eventClassService;

    public EventClassController(EventClassService eventClassService) {
        this.eventClassService = eventClassService;
    }

    /**
     * Adds a racing class to the event with a snapshot of a race format template (FORMAT-06), so later template edits
     * do not change this event. {@code POST}; ADMIN; answers 201. 404 for an unknown event, class or template.
     * Audited.
     */
    @Audited("audit_log")
    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public EventClassDto addClassToEvent(@PathVariable Long eventId,
                                          @RequestBody @Valid AddEventClassRequest request) {
        return EventClassDto.from(eventClassService.addClassToEvent(CurrentOfficial.actor(), eventId,
                request.racingClassId(), request.templateId()));
    }

    /**
     * Removes a class from the event. {@code DELETE /{classId}}; ADMIN; answers 204. 409 if races have been generated
     * for it, or it has active entries and {@code withdrawEntries} is not true. With it, the entries are withdrawn
     * first. 404 for an unknown class, 400 if it belongs to another event. Audited.
     */
    @Audited("audit_log")
    @DeleteMapping("/{classId}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeClassFromEvent(@PathVariable Long eventId, @PathVariable Long classId,
                                     @RequestParam(defaultValue = "false") boolean withdrawEntries) {
        eventClassService.removeClassFromEvent(CurrentOfficial.actor(), eventId, classId, withdrawEntries);
    }

    /**
     * Sets the event class's format override, a patch laid over its snapshot (FORMAT-07); an empty override clears
     * it. {@code PUT /{classId}/overrides}; ADMIN. 400 if the override does not fit the format. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/{classId}/overrides")
    @AdminOnly
    public EventClassDto updateOverrides(@PathVariable Long eventId,
                                          @PathVariable Long classId,
                                          @RequestBody @Valid UpdateEventClassOverrideRequest request) {
        return EventClassDto.from(eventClassService.updateOverrides(CurrentOfficial.actor(), eventId, classId,
                request.override()));
    }

    /**
     * Makes the given event classes race together but score separately (EVENT-06). {@code POST /combine}; ADMIN.
     * 400 for fewer than two classes or one that is not in the event. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/combine")
    @AdminOnly
    public List<EventClassDto> combineClasses(@PathVariable Long eventId,
                                               @RequestBody @Valid CombineClassesRequest request) {
        return eventClassService.combineClasses(CurrentOfficial.actor(), eventId, request.eventClassIds()).stream()
                .map(EventClassDto::from)
                .toList();
    }
}
