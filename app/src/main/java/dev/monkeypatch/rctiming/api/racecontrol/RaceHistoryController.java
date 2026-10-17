package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.query.racecontrol.RaceHistoryDto;
import dev.monkeypatch.rctiming.query.racecontrol.RaceHistoryQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only account of what happened in a race (#140): lifecycle, penalties, incidents, marshal laps and links.
 * Under {@code /api/v1/race-control/race/{raceId}/history}; ADMIN, RACE_DIRECTOR or REFEREE.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}/history")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class RaceHistoryController {

    private final RaceHistoryQuery history;
    private final RaceRepository raceRepository;

    public RaceHistoryController(RaceHistoryQuery history, RaceRepository raceRepository) {
        this.history = history;
        this.raceRepository = raceRepository;
    }

    /** The race's history entries, as {@link RaceHistoryDto} rows. {@code GET}; 404 if the race does not exist. */
    @GetMapping
    public List<RaceHistoryDto> getHistory(@PathVariable long raceId) {
        raceRepository.requireExists(raceId);
        return history.forRace(raceId);
    }
}
