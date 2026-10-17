package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
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
 * REST endpoint for the pre-race readiness data (CTRL-02, CTRL-07), under {@code /api/v1/race-control/race}.
 * Role-gated to RACE_DIRECTOR and ADMIN; other officials receive 403.
 * EntityNotFoundException from the query surfaces as 404 via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/race-control/race")
public class PreRaceReadinessController {

    private final PreRaceReadinessQuery query;
    private final RaceRepository raceRepository;
    private final EventClassRepository eventClassRepository;
    private final RoundRepository roundRepository;
    private final RaceFormatService raceFormatService;

    public PreRaceReadinessController(PreRaceReadinessQuery query, RaceRepository raceRepository,
                                      EventClassRepository eventClassRepository, RoundRepository roundRepository,
                                      RaceFormatService raceFormatService) {
        this.query = query;
        this.raceRepository = raceRepository;
        this.eventClassRepository = eventClassRepository;
        this.roundRepository = roundRepository;
        this.raceFormatService = raceFormatService;
    }

    /**
     * The grid call list and the marshal duty list for a race, with how it starts (the race's own start type, or the
     * format's when the race has none saved) and the stagger interval in seconds.
     * {@code GET /{raceId}/pre-race-readiness}; RACE_DIRECTOR or ADMIN. Read only; 404 if the race does not exist.
     */
    @GetMapping("/{raceId}/pre-race-readiness")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    public PreRaceReadinessDto get(@PathVariable long raceId) {
        PreRaceReadinessDto readiness = query.load(raceId);
        Race race = raceRepository.getOrThrow(raceId);
        EventClass eventClass = eventClassRepository.findById(race.getEventClassId()).orElse(null);
        // A race made before it kept its start type takes the format's start, as the round generator would
        StartType startType = race.getStartType() != null ? race.getStartType()
                : raceFormatService.startType(eventClass, roundRepository.getOrThrow(race.getRoundId()).getType());
        return readiness.withStart(startType.name(), raceFormatService.staggerIntervalSeconds(eventClass));
    }
}
