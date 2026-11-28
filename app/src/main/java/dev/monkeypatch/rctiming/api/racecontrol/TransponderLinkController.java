package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.query.racecontrol.RaceEntriesQuery;
import dev.monkeypatch.rctiming.query.racecontrol.RaceEntryDto;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import dev.monkeypatch.rctiming.domain.racecontrol.TransponderLinkService;
import dev.monkeypatch.rctiming.domain.racecontrol.RaceControlService;
import dev.monkeypatch.rctiming.timing.dto.LinkTransponderRequestDto;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDecisionRequestDto;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
 * Under {@code /api/v1/race-control/race/{raceId}}.
 */
@RestController
@RequestMapping("/api/v1/race-control/race/{raceId}")
public class TransponderLinkController {

    private final RaceEntriesQuery raceEntriesQuery;
    private final TransponderLinkService transponderLinkService;
    private final RaceControlService raceControlService;

    public TransponderLinkController(RaceEntriesQuery raceEntriesQuery,
                                     TransponderLinkService transponderLinkService,
                                     RaceControlService raceControlService) {
        this.raceControlService = raceControlService;
        this.raceEntriesQuery = raceEntriesQuery;
        this.transponderLinkService = transponderLinkService;
    }

    /**
     * Everyone in the race, in any state. The race director picks from it to link a transponder, and
     * the referee to pick a driver for an incident or penalty, which can come after the finish when
     * live timing is empty (#107). {@code GET /entries}; RACE_DIRECTOR, REFEREE or ADMIN. Read only.
     */
    @GetMapping("/entries")
    @RaceControlStaff
    public ResponseEntity<List<RaceEntryDto>> getEntries(@PathVariable Long raceId) {
        return ResponseEntity.ok(raceEntriesQuery.findForRace(raceId));
    }

    /**
     * Links the transponder and answers with how many laps it credited ({@code lapsCredited}); see
     * {@link TransponderLinkService#link}. {@code POST /transponders/link}; RACE_DIRECTOR or ADMIN. 409 unless the
     * race is running or stopped, if the number is already linked to another entry, or if the entry has already
     * passed in this race; 400 if the entry is not in the race. Recorded in the audit log, and the number is saved
     * on the entry too.
     */
    @Audited("audit_log")
    @PostMapping("/transponders/link")
    @DirectorOrAdmin
    public ResponseEntity<Map<String, Integer>> linkTransponder(
            @PathVariable Long raceId,
            @Valid @RequestBody LinkTransponderRequestDto request) {
        int lapsCredited = transponderLinkService.link(
                raceId, request.transponderNumber(), request.entryId(), CurrentOfficial.id());
        return ResponseEntity.ok(Map.of("lapsCredited", lapsCredited));
    }

    /**
     * The drivers seen in this race who are in another heat, for a screen that opens mid-race; changes arrive on
     * {@code /topic/race/{raceId}/wrong-heat}. {@code GET /wrong-heat}; RACE_DIRECTOR, REFEREE or ADMIN. 404 if the
     * race does not exist.
     */
    @GetMapping("/wrong-heat")
    @RaceControlStaff
    public List<WrongHeatDto> getWrongHeat(@PathVariable Long raceId) {
        return raceControlService.wrongHeats(raceId);
    }

    /**
     * Counts a driver seen in the wrong heat: adds them to the race and credits their laps, answering with
     * {@code lapsCredited}; see {@link TransponderLinkService#approveWrongHeat}. {@code POST /wrong-heat/approve};
     * RACE_DIRECTOR, REFEREE or ADMIN. 409 unless the race is running or stopped and the number is flagged. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/wrong-heat/approve")
    @RaceControlStaff
    public ResponseEntity<Map<String, Integer>> approveWrongHeat(
            @PathVariable Long raceId, @Valid @RequestBody WrongHeatDecisionRequestDto request) {
        int lapsCredited = transponderLinkService.approveWrongHeat(
                raceId, request.transponderNumber(), CurrentOfficial.id());
        return ResponseEntity.ok(Map.of("lapsCredited", lapsCredited));
    }

    /**
     * Keeps a driver seen in the wrong heat out of the results; see {@link TransponderLinkService#disapproveWrongHeat}.
     * {@code POST /wrong-heat/disapprove}; RACE_DIRECTOR, REFEREE or ADMIN. 409 unless the race is running or stopped
     * and the number is flagged and not approved. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/wrong-heat/disapprove")
    @RaceControlStaff
    public ResponseEntity<Void> disapproveWrongHeat(
            @PathVariable Long raceId, @Valid @RequestBody WrongHeatDecisionRequestDto request) {
        transponderLinkService.disapproveWrongHeat(raceId, request.transponderNumber(), CurrentOfficial.id());
        return ResponseEntity.noContent().build();
    }
}
