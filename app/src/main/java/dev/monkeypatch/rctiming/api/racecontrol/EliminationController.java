package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.RaceEliminationDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.RaceEliminationRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.RaceEliminationsDto;
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

/**
 * Shikabane eliminations, zombie revivals and false starts, marked by hand from race control. Under
 * {@code /api/v1/race-control/race/{raceId}/eliminations}; reading is open to RACE_DIRECTOR, REFEREE or ADMIN and marking to RACE_DIRECTOR or ADMIN. {@link RaceEliminationService}
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

    /**
     * Whether the race is a Shikabane race, and its eliminations and revivals, oldest first. {@code GET}; 404 for a
     * missing race. Race control shows its elimination buttons only when {@code shikabane} is true.
     */
    @GetMapping
    public RaceEliminationsDto list(@PathVariable long raceId) {
        return new RaceEliminationsDto(eliminationService.isShikabane(raceId),
                eliminationService.list(raceId).stream().map(RaceEliminationDto::from).toList());
    }

    /** Marks a car dead. {@code POST .../eliminate}; 200 with the row, 400 for a bad body. Audited. */
    @Audited("audit_log")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    @PostMapping("/eliminate")
    public ResponseEntity<RaceEliminationDto> eliminate(@PathVariable long raceId,
                                                        @Valid @RequestBody RaceEliminationRequest req) {
        return ResponseEntity.ok(RaceEliminationDto.from(
                eliminationService.eliminate(raceId, req.entryId(), CurrentOfficial.id())));
    }

    /** Revives a dead car (a zombie). {@code POST .../revive}; 200 with the row. Audited. */
    @Audited("audit_log")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    @PostMapping("/revive")
    public ResponseEntity<RaceEliminationDto> revive(@PathVariable long raceId,
                                                     @Valid @RequestBody RaceEliminationRequest req) {
        return ResponseEntity.ok(RaceEliminationDto.from(
                eliminationService.revive(raceId, req.entryId(), CurrentOfficial.id())));
    }

    /** Takes back an elimination made by mistake. {@code POST .../undo}; 200. Audited. */
    @Audited("audit_log")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    @PostMapping("/undo")
    public ResponseEntity<Void> undo(@PathVariable long raceId, @Valid @RequestBody RaceEliminationRequest req) {
        eliminationService.undoElimination(raceId, req.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }

    /**
     * Gives a car the format's false start penalty points. {@code POST .../false-start}; 200. Audited. Can be given
     * to the same car more than once.
     */
    @Audited("audit_log")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
    @PostMapping("/false-start")
    public ResponseEntity<Void> falseStart(@PathVariable long raceId, @Valid @RequestBody RaceEliminationRequest req) {
        eliminationService.falseStart(raceId, req.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }
}
