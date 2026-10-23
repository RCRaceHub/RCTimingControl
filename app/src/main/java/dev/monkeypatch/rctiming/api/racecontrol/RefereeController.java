package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.IncidentReportDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.IncidentReportRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalAbsenceDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalAbsenceRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalPenaltyDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalPenaltyRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.PenaltyDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.PenaltyRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.RefereeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for referee actions: incident reports, penalties and marshal absences (OFFICIAL-03, OFFICIAL-04).
 * All endpoints require the REFEREE or ADMIN role (a race director does not get them unless also a referee).
 * {@link RefereeService} does the work and writes the audit rows. Under {@code /api/v1/race-control/referee}; each
 * answers 200 with the record it saved, except a jump start dismissal, which answers 204.
 */
@RestController
@RequestMapping("/api/v1/race-control/referee")
@PreAuthorize("hasAnyRole('REFEREE','ADMIN')")
public class RefereeController {

    private final RefereeService refereeService;

    public RefereeController(RefereeService refereeService) {
        this.refereeService = refereeService;
    }

    /**
     * Raises an incident report against an entry in the race. {@code POST /race/{raceId}/incident-report}; REFEREE
     * or ADMIN; 400 for a bad body. Audited as {@code INCIDENT_RAISED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/incident-report")
    public ResponseEntity<IncidentReportDto> raiseIncident(@PathVariable long raceId,
                                                            @Valid @RequestBody IncidentReportRequest req) {
        return ResponseEntity.ok(IncidentReportDto.from(refereeService.raiseIncident(
                raceId, req.entryId(), req.incidentType(), req.description(), CurrentOfficial.id())));
    }

    /** A LAP penalty comes off live timing straight away; a TIME penalty is added to a running
     * race's live times at once, and to the result.
     * {@code POST /race/{raceId}/penalty}; REFEREE or ADMIN. 400 for a bad body, or a LAP penalty that is not a whole
     * number of laps within the limit. Audited as {@code PENALTY_APPLIED}; on a finished race its results are sent
     * again (#27). */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/penalty")
    public ResponseEntity<PenaltyDto> applyPenalty(@PathVariable long raceId,
                                                   @Valid @RequestBody PenaltyRequest req) {
        return ResponseEntity.ok(PenaltyDto.from(refereeService.applyPenalty(
                raceId, req.entryId(), req.penaltyType(), req.value(), req.reason(), CurrentOfficial.id())));
    }

    /**
     * Takes no action on a car's jump start: the alert goes from the referee screens and the car keeps its lozenge.
     * {@code POST /race/{raceId}/jump-start/{entryId}/dismiss}; REFEREE or ADMIN. 409 if the entry has no open jump start in
     * the race in progress. Audited as {@code JUMP_START_DISMISSED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/jump-start/{entryId}/dismiss")
    public ResponseEntity<Void> dismissJumpStart(@PathVariable long raceId, @PathVariable long entryId) {
        refereeService.dismissJumpStart(raceId, entryId, CurrentOfficial.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Record that an entry missed their marshal duty.
     * Does NOT auto-create a penalty — use /apply-marshal-penalty for that (which also records the miss itself).
     * {@code POST /race/{raceId}/marshal-absent}; REFEREE or ADMIN. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/marshal-absent")
    public ResponseEntity<MarshalAbsenceDto> recordMarshalAbsent(@PathVariable long raceId,
                                                                 @Valid @RequestBody MarshalAbsenceRequest req) {
        return ResponseEntity.ok(MarshalAbsenceDto.from(refereeService.recordMarshalAbsence(
                raceId, req.entryId(), req.eventId(), CurrentOfficial.id())));
    }

    /**
     * Apply a marshal penalty for a missed marshal duty. Marshals are assumed present, so recording the miss first
     * is optional. The request can name the absence (its id comes back from {@code marshal-absent}); otherwise the
     * entry's most recent absence in the event without a penalty is used, and if there is none the miss is
     * recorded by this call. {@code POST /race/{raceId}/apply-marshal-penalty}; REFEREE or
     * ADMIN. 409 if the entry is not in the event, or the absence already has a penalty. Audited as
     * {@code MARSHAL_PENALTY_APPLIED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/apply-marshal-penalty")
    public ResponseEntity<MarshalPenaltyDto> applyMarshalPenalty(@PathVariable long raceId,
                                                                 @Valid @RequestBody MarshalPenaltyRequest req) {
        return ResponseEntity.ok(MarshalPenaltyDto.from(refereeService.applyMarshalPenalty(
                raceId, req.entryId(), req.eventId(), req.kind(), req.absenceId(), req.notes(), CurrentOfficial.id())));
    }
}
