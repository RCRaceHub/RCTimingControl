package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessDto;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.service.RaceControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the pre-race readiness data (CTRL-02, CTRL-07), under {@code /api/v1/race-control/race}.
 * Role-gated to RACE_DIRECTOR and ADMIN; other officials receive 403.
 * EntityNotFoundException from the service surfaces as 404 via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/race-control/race")
public class PreRaceReadinessController {

    private final RaceControlService raceControlService;

    public PreRaceReadinessController(RaceControlService raceControlService) {
        this.raceControlService = raceControlService;
    }

    /**
     * The grid call list and the marshal duty list for a race, with how it starts (the race's own start type, or the
     * format's when the race has none saved), the stagger interval in seconds and the entries the decoder has seen on
     * the warm-up lap.
     * {@code GET /{raceId}/pre-race-readiness}; RACE_DIRECTOR or ADMIN. Read only; 404 if the race does not exist.
     */
    @GetMapping("/{raceId}/pre-race-readiness")
    @DirectorOrAdmin
    public PreRaceReadinessDto get(@PathVariable long raceId) {
        return raceControlService.preRaceReadiness(raceId);
    }
}
