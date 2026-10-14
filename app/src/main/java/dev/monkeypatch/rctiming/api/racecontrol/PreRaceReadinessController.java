package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessDto;
import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the pre-race readiness data (CTRL-02, CTRL-07).
 * Role-gated to RACE_DIRECTOR and ADMIN; other officials receive 403.
 * EntityNotFoundException from the query surfaces as 404 via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/race-control/race")
public class PreRaceReadinessController {

    private final PreRaceReadinessQuery query;

    public PreRaceReadinessController(PreRaceReadinessQuery query) {
        this.query = query;
    }

    @GetMapping("/{raceId}/pre-race-readiness")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public PreRaceReadinessDto get(@PathVariable long raceId) {
        return query.load(raceId);
    }
}
