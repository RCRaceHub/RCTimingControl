package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.domain.event.FinalLegsScope;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which finals of an event are run in legs: the A final only, or every final. Under
 * {@code /api/v1/race-control/events/{eventId}/final-legs-scope}. Any official can read it; changing it needs
 * RACE_DIRECTOR or ADMIN. It is read when the rounds are generated, so it must be set before then.
 */
@RestController
@RequestMapping("/api/v1/race-control")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class FinalLegsScopeController {

    private final EventService eventService;

    public FinalLegsScopeController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * The body of the update and the answer of both calls.
     *
     * @param scope {@code A_ONLY} or {@code ALL}
     */
    public record FinalLegsScopeDto(@NotNull FinalLegsScope scope) {
    }

    /** The event's setting. {@code GET}; 404 if the event does not exist. */
    @GetMapping("/events/{eventId}/final-legs-scope")
    public FinalLegsScopeDto get(@PathVariable long eventId) {
        return new FinalLegsScopeDto(eventService.findByIdOrThrow(eventId).getFinalLegsScope());
    }

    /**
     * Changes the setting and answers with the one now in force. {@code PUT}; RACE_DIRECTOR or ADMIN; 404 if the
     * event does not exist, 400 for a value that is not one of the two. Recorded in the audit log only when it
     * changes. Finals already calculated keep their legs.
     */
    @Audited("audit_log")
    @PutMapping("/events/{eventId}/final-legs-scope")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public FinalLegsScopeDto update(@PathVariable long eventId,
                                    @Valid @RequestBody FinalLegsScopeDto request) {
        return new FinalLegsScopeDto(
                eventService.setFinalLegsScope(CurrentOfficial.actor(), eventId, request.scope()));
    }
}
