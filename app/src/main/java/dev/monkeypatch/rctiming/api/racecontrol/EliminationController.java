package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.RaceEliminationDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.RaceEliminationRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.RaceEliminationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Shikabane eliminations and zombie revivals, marked by hand from race control. Under
 * {@code /api/v1/race-control/race/{raceId}/eliminations}; RACE_DIRECTOR, REFEREE or ADMIN. {@link RaceEliminationService}
 * does the work and writes the audit rows. Each action answers 409 when the race is not a Shikabane race, has not
 * started, or the car is not in the state the action needs.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}/eliminations")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class EliminationController {

    private final RaceEliminationService eliminationService;

    public EliminationController(RaceEliminationService eliminationService) {
        this.eliminationService = eliminationService;
    }

    /** The race's eliminations and revivals, oldest first. {@code GET}; 404 for a missing race. */
    @GetMapping
    public List<RaceEliminationDto> list(@PathVariable long raceId) {
        return eliminationService.list(raceId).stream().map(RaceEliminationDto::from).toList();
    }

    /** Marks a car dead. {@code POST .../eliminate}; 200 with the row, 400 for a bad body. Audited. */
    @Audited("audit_log")
    @PostMapping("/eliminate")
    public ResponseEntity<RaceEliminationDto> eliminate(@PathVariable long raceId,
                                                        @Valid @RequestBody RaceEliminationRequest req) {
        return ResponseEntity.ok(RaceEliminationDto.from(
                eliminationService.eliminate(raceId, req.entryId(), CurrentOfficial.id())));
    }

    /** Revives a dead car (a zombie). {@code POST .../revive}; 200 with the row. Audited. */
    @Audited("audit_log")
    @PostMapping("/revive")
    public ResponseEntity<RaceEliminationDto> revive(@PathVariable long raceId,
                                                     @Valid @RequestBody RaceEliminationRequest req) {
        return ResponseEntity.ok(RaceEliminationDto.from(
                eliminationService.revive(raceId, req.entryId(), CurrentOfficial.id())));
    }

    /** Takes back an elimination made by mistake. {@code POST .../undo}; 200. Audited. */
    @Audited("audit_log")
    @PostMapping("/undo")
    public ResponseEntity<Void> undo(@PathVariable long raceId, @Valid @RequestBody RaceEliminationRequest req) {
        eliminationService.undoElimination(raceId, req.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }
}
