package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.query.racecontrol.RaceEntriesQuery;
import dev.monkeypatch.rctiming.query.racecontrol.RaceEntryDto;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.TransponderLinkService;
import dev.monkeypatch.rctiming.timing.dto.LinkTransponderRequestDto;
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
import java.util.Map;

/**
 * Retroactive transponder linking during a live race (TIMING-08, CTRL-06), and the race's entries, which the race
 * director links to and the referee picks from. A race director or admin can link an unknown transponder number to an
 * existing entry, crediting all passings since race start; {@link TransponderLinkService} writes the audit rows.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}")
public class TransponderLinkController {

    private final RaceEntriesQuery raceEntriesQuery;
    private final TransponderLinkService transponderLinkService;

    public TransponderLinkController(RaceEntriesQuery raceEntriesQuery,
                                     TransponderLinkService transponderLinkService) {
        this.raceEntriesQuery = raceEntriesQuery;
        this.transponderLinkService = transponderLinkService;
    }

    /**
     * Everyone in the race, in any state. The race director picks from it to link a transponder, and
     * the referee to pick a driver for an incident or penalty, which can come after the finish when
     * live timing is empty (#107).
     */
    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR', 'REFEREE', 'ADMIN')")
    public ResponseEntity<List<RaceEntryDto>> getEntries(@PathVariable Long raceId) {
        return ResponseEntity.ok(raceEntriesQuery.findForRace(raceId));
    }

    /** Links the transponder and answers with how many laps it credited; see {@link TransponderLinkService#link}. */
    @Audited("audit_log")
    @PostMapping("/transponders/link")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR', 'ADMIN')")
    public ResponseEntity<Map<String, Integer>> linkTransponder(
            @PathVariable Long raceId,
            @Valid @RequestBody LinkTransponderRequestDto request) {
        int lapsCredited = transponderLinkService.link(
                raceId, request.transponderNumber(), request.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok(Map.of("lapsCredited", lapsCredited));
    }
}
