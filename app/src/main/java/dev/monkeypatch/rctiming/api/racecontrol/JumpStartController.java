package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.security.RaceControlStaff;
import dev.monkeypatch.rctiming.service.RaceControlService;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
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
@RaceControlStaff
public class JumpStartController {

    private final RaceControlService raceControlService;

    public JumpStartController(RaceControlService raceControlService) {
        this.raceControlService = raceControlService;
    }

    /**
     * The jump starts so far, in the order they happened; empty when none, or when the race is not in progress
     * (they are kept in memory with the race's live timing). {@code GET}; 404 if the race does not exist.
     */
    @GetMapping
    public List<JumpStartDto> getJumpStarts(@PathVariable long raceId) {
        return raceControlService.jumpStarts(raceId);
    }
}
