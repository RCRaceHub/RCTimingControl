package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
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
    private final RaceRepository raceRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;

    public PreRaceReadinessController(PreRaceReadinessQuery query, RaceRepository raceRepository,
                                      EventClassRepository eventClassRepository,
                                      RaceFormatService raceFormatService) {
        this.query = query;
        this.raceRepository = raceRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
    }

    @GetMapping("/{raceId}/pre-race-readiness")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public PreRaceReadinessDto get(@PathVariable long raceId) {
        PreRaceReadinessDto readiness = query.load(raceId);
        Race race = raceRepository.getOrThrow(raceId);
        int interval = raceFormatService.staggerIntervalSeconds(
                eventClassRepository.findById(race.getEventClassId()).orElse(null));
        return readiness.withStart(race.getStartType() == null ? null : race.getStartType().name(), interval);
    }
}
