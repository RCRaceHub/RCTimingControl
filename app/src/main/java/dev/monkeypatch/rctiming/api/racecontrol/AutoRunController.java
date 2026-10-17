package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.AutoRunService;
import dev.monkeypatch.rctiming.service.AutoRunService.AutoRunState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automatic race progression (#338): switching it on and off for an event, bringing the next start forward, and
 * the gap and finals hold it uses. Under {@code /api/v1/race-control/events/{eventId}/auto-run}. The state may be
 * read by the RACE_DIRECTOR, REFEREE or ADMIN role; every change needs RACE_DIRECTOR or ADMIN. All answer with the
 * event's {@link AutoRunState}, and an unknown event is a 404. Switching it on or off, bringing a start forward and
 * changing the settings are recorded in the audit log. The plan itself is held in memory, so a restart of the app
 * switches auto run off.
 */
@RestController
@RequestMapping("/api/v1/race-control/events/{eventId}/auto-run")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class AutoRunController {

    private final AutoRunService autoRunService;
    private final EventService eventService;

    public AutoRunController(AutoRunService autoRunService, EventService eventService) {
        this.autoRunService = autoRunService;
        this.eventService = eventService;
    }

    /**
     * The body of {@code POST /skip-to}.
     *
     * @param seconds how long from now the next race should start, 0 to 600
     */
    public record SkipRequest(@NotNull @Min(0) @Max(600) Integer seconds) {
    }

    /**
     * The body of {@code PUT /settings}.
     *
     * @param gapSeconds       the time between a race finishing and the next starting, 0 to 3600
     * @param holdBeforeFinals whether auto run stops after the last qualifier
     * @param autoStart        whether auto run switches itself on when a race is started by hand
     */
    public record AutoRunSettingsRequest(@NotNull @Min(0) @Max(3600) Integer gapSeconds,
                                         @NotNull Boolean holdBeforeFinals,
                                         @NotNull Boolean autoStart) {
    }

    /** Whether auto run is on for the event and what it will do next. {@code GET}; read only. */
    @GetMapping
    public AutoRunState state(@PathVariable long eventId) {
        return autoRunService.state(eventId);
    }

    /**
     * Switches auto run on for the event, or releases a hold (such as the one before the finals), and plans the next
     * race. {@code POST /go}; RACE_DIRECTOR or ADMIN.
     */
    @Audited("audit_log")
    @PostMapping("/go")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState go(@PathVariable long eventId) {
        return autoRunService.go(CurrentOfficial.actor(), eventId);
    }

    /**
     * Switches auto run off. A race already called to the grid or running is left as it is. {@code POST /stop};
     * RACE_DIRECTOR or ADMIN.
     */
    @Audited("audit_log")
    @PostMapping("/stop")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState stop(@PathVariable long eventId) {
        return autoRunService.stop(CurrentOfficial.actor(), eventId);
    }

    /**
     * Brings the next race's start forward to the given seconds from now, if it is waiting to start later than
     * that. {@code POST /skip-to}; RACE_DIRECTOR or ADMIN; 400 if the seconds are outside 0 to 600.
     */
    @Audited("audit_log")
    @PostMapping("/skip-to")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState skipTo(@PathVariable long eventId, @Valid @RequestBody SkipRequest request) {
        return autoRunService.skipTo(CurrentOfficial.actor(), eventId, request.seconds());
    }

    /**
     * Sets the event's gap between races and whether it holds before the finals, then pushes the new state to open
     * race control pages. {@code PUT /settings}; RACE_DIRECTOR or ADMIN; 400 if a value is out of range.
     */
    @Audited("audit_log")
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState settings(@PathVariable long eventId, @Valid @RequestBody AutoRunSettingsRequest request) {
        eventService.setAutoRunSettings(CurrentOfficial.actor(), eventId, request.gapSeconds(),
                request.holdBeforeFinals(), request.autoStart());
        return autoRunService.refresh(eventId);
    }
}
