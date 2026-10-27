package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.domain.event.PracticeRanking;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How an event's practice is ranked on the live top board: fastest lap, or the best 2 or 3 laps in a row. Under
 * {@code /api/v1/race-control/events/{eventId}/practice-ranking}. Any official can read it; changing it needs
 * RACE_DIRECTOR or ADMIN.
 */
@RestController
@RequestMapping("/api/v1/race-control")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class PracticeRankingController {

    private final EventService eventService;

    public PracticeRankingController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * The body of the update and the answer of both calls.
     *
     * @param ranking {@code FASTEST}, {@code BEST_2} or {@code BEST_3}
     */
    public record PracticeRankingDto(@NotNull PracticeRanking ranking) {
    }

    /** The event's practice ranking. {@code GET}; 404 if the event does not exist. */
    @GetMapping("/events/{eventId}/practice-ranking")
    public PracticeRankingDto get(@PathVariable long eventId) {
        return new PracticeRankingDto(eventService.findByIdOrThrow(eventId).getPracticeRanking());
    }

    /**
     * Changes the event's practice ranking and answers with the one now in force. {@code PUT}; RACE_DIRECTOR or
     * ADMIN; 404 if the event does not exist, 400 for a value that is not one of the three. Recorded in the audit
     * log only when it changes.
     */
    @Audited("audit_log")
    @PutMapping("/events/{eventId}/practice-ranking")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public PracticeRankingDto update(Authentication auth, @PathVariable long eventId,
                                     @Valid @RequestBody PracticeRankingDto request) {
        return new PracticeRankingDto(
                eventService.setPracticeRanking(CurrentOfficial.actor(auth), eventId, request.ranking()));
    }
}
