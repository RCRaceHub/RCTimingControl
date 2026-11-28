package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.DidNotStartDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.DidNotStartRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racecontrol.DidNotStartService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cars marked as not started (DNS) from race control. Under {@code /api/v1/race-control/race/{raceId}/dns}; reading
 * is open to RACE_DIRECTOR, REFEREE or ADMIN and marking to RACE_DIRECTOR or ADMIN. {@link DidNotStartService}
 * does the work and writes the audit rows; 409 when the entry is not in the race or has already completed laps.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}/dns")
@RaceControlStaff
public class DidNotStartController {

    private final DidNotStartService didNotStartService;

    public DidNotStartController(DidNotStartService didNotStartService) {
        this.didNotStartService = didNotStartService;
    }

    /** The cars marked as not started. {@code GET}; 404 for a missing race. */
    @GetMapping
    public DidNotStartDto list(@PathVariable long raceId) {
        return new DidNotStartDto(didNotStartService.list(raceId));
    }

    /** Marks a car as not started. {@code POST}; 200, 400 for a bad body, 409 as above. Audited. */
    @Audited("audit_log")
    @DirectorOrAdmin
    @PostMapping
    public ResponseEntity<Void> mark(@PathVariable long raceId, @Valid @RequestBody DidNotStartRequest req) {
        didNotStartService.markDidNotStart(raceId, req.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }

    /** Takes the mark back. {@code POST .../undo}; 200, 409 if the car is not marked. Audited. */
    @Audited("audit_log")
    @DirectorOrAdmin
    @PostMapping("/undo")
    public ResponseEntity<Void> undo(@PathVariable long raceId, @Valid @RequestBody DidNotStartRequest req) {
        didNotStartService.undoDidNotStart(raceId, req.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }
}
