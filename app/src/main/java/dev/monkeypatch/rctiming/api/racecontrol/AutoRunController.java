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
 * the gap and finals hold it uses.
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

    /** @param seconds how long from now the next race should start */
    public record SkipRequest(@NotNull @Min(0) @Max(600) Integer seconds) {
    }

    /**
     * @param gapSeconds       the time between a race finishing and the next starting
     * @param holdBeforeFinals whether auto run stops after the last qualifier
     */
    public record AutoRunSettingsRequest(@NotNull @Min(0) @Max(3600) Integer gapSeconds,
                                         @NotNull Boolean holdBeforeFinals) {
    }

    @GetMapping
    public AutoRunState state(@PathVariable long eventId) {
        return autoRunService.state(eventId);
    }

    @Audited("audit_log")
    @PostMapping("/go")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState go(@PathVariable long eventId) {
        return autoRunService.go(CurrentOfficial.actor(), eventId);
    }

    @Audited("audit_log")
    @PostMapping("/stop")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState stop(@PathVariable long eventId) {
        return autoRunService.stop(CurrentOfficial.actor(), eventId);
    }

    @Audited("audit_log")
    @PostMapping("/skip-to")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState skipTo(@PathVariable long eventId, @Valid @RequestBody SkipRequest request) {
        return autoRunService.skipTo(CurrentOfficial.actor(), eventId, request.seconds());
    }

    @Audited("audit_log")
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public AutoRunState settings(@PathVariable long eventId, @Valid @RequestBody AutoRunSettingsRequest request) {
        eventService.setAutoRunSettings(CurrentOfficial.actor(), eventId, request.gapSeconds(),
                request.holdBeforeFinals());
        return autoRunService.refresh(eventId);
    }
}
