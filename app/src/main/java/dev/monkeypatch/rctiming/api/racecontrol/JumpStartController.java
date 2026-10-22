package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The cars that have crossed the line before their turn in a stagger start, for a referee screen that opens
 * mid-race; later ones arrive on {@code /topic/race/{raceId}/jump-start}. Under
 * {@code /api/v1/race-control/race/{raceId}/jump-starts}; ADMIN, RACE_DIRECTOR or REFEREE.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}/jump-starts")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class JumpStartController {

    private final LapTimingService lapTimingService;
    private final RaceRepository raceRepository;

    public JumpStartController(LapTimingService lapTimingService, RaceRepository raceRepository) {
        this.lapTimingService = lapTimingService;
        this.raceRepository = raceRepository;
    }

    /**
     * The jump starts so far, in the order they happened; empty when none, or when the race is not in progress
     * (they are kept in memory with the race's live timing). {@code GET}; 404 if the race does not exist.
     */
    @GetMapping
    public List<JumpStartDto> getJumpStarts(@PathVariable long raceId) {
        raceRepository.requireExists(raceId);
        return lapTimingService.jumpStartsOf(raceId);
    }
}
