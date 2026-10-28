package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.livefeed.LiveFeedPublisher;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.timing.dto.LiveFeedStatusDto;
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
 * The live feed for remote viewers (#28): its connection to the relay, and whether an event's races are sent.
 * Under {@code /api/v1/race-control}. The reads are open to the RACE_DIRECTOR, REFEREE and ADMIN roles; switching
 * an event's feed on or off needs RACE_DIRECTOR or ADMIN.
 */
@RestController
@RequestMapping("/api/v1/race-control")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class LiveFeedController {

    private final LiveFeedPublisher publisher;
    private final EventService eventService;

    public LiveFeedController(LiveFeedPublisher publisher, EventService eventService) {
        this.publisher = publisher;
        this.eventService = eventService;
    }

    /**
     * An event's live feed switch: the body of {@code PUT /events/{eventId}/live-feed} and its answer.
     *
     * @param enabled whether the event's races are sent to the relay while they run
     */
    public record LiveFeedSettingDto(@NotNull Boolean enabled) {
    }

    /** Whether the live feed is connected to its relay. {@code GET /live-feed/status}; read only. */
    @GetMapping("/live-feed/status")
    public LiveFeedStatusDto status() {
        return publisher.status();
    }

    /** Whether the event's races are sent. {@code GET /events/{eventId}/live-feed}; 404 if the event does not exist. */
    @GetMapping("/events/{eventId}/live-feed")
    public LiveFeedSettingDto setting(@PathVariable long eventId) {
        return new LiveFeedSettingDto(eventService.findByIdOrThrow(eventId).isLiveFeedEnabled());
    }

    /**
     * Turns the feed on or off for the event and answers with whether it is now on.
     * {@code PUT /events/{eventId}/live-feed}; RACE_DIRECTOR or ADMIN; 404 if the event does not exist. The switch is
     * recorded in the audit log only when it changes.
     */
    @Audited("audit_log")
    @PutMapping("/events/{eventId}/live-feed")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public LiveFeedSettingDto update(@PathVariable long eventId,
                                     @Valid @RequestBody LiveFeedSettingDto request) {
        return new LiveFeedSettingDto(
                eventService.setLiveFeedEnabled(CurrentOfficial.actor(), eventId, request.enabled()));
    }
}
