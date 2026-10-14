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
 * All endpoints require the REFEREE or ADMIN role. {@link RefereeService} does the work and writes the audit rows.
 */
@RestController
@RequestMapping("/api/v1/race-control/referee")
@PreAuthorize("hasAnyRole('REFEREE','ADMIN')")
public class RefereeController {

    private final RefereeService refereeService;

    public RefereeController(RefereeService refereeService) {
        this.refereeService = refereeService;
    }

    @Audited("audit_log")
    @PostMapping("/race/{raceId}/incident-report")
    public ResponseEntity<IncidentReportDto> raiseIncident(@PathVariable long raceId,
                                                            @Valid @RequestBody IncidentReportRequest req) {
        return ResponseEntity.ok(IncidentReportDto.from(refereeService.raiseIncident(
                raceId, req.entryId(), req.incidentType(), req.description(), CurrentOfficial.id())));
    }

    /** A LAP penalty comes off live timing straight away; a TIME penalty is added when the result is worked out. */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/penalty")
    public ResponseEntity<PenaltyDto> applyPenalty(@PathVariable long raceId,
                                                   @Valid @RequestBody PenaltyRequest req) {
        return ResponseEntity.ok(PenaltyDto.from(refereeService.applyPenalty(
                raceId, req.entryId(), req.penaltyType(), req.value(), req.reason(), CurrentOfficial.id())));
    }

    /**
     * Record that an entry missed their marshal duty.
     * Does NOT auto-create a penalty — use /apply-marshal-penalty for that.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/marshal-absent")
    public ResponseEntity<MarshalAbsenceDto> recordMarshalAbsent(@PathVariable long raceId,
                                                                 @Valid @RequestBody MarshalAbsenceRequest req) {
        return ResponseEntity.ok(MarshalAbsenceDto.from(refereeService.recordMarshalAbsence(
                raceId, req.entryId(), req.eventId(), CurrentOfficial.id())));
    }

    /**
     * Apply a marshal penalty for a recorded absence, a separate action from recording it. The request can
     * name the absence (its id comes back from {@code marshal-absent}); otherwise the entry's most recent
     * absence in the event is used, if there is one.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/apply-marshal-penalty")
    public ResponseEntity<MarshalPenaltyDto> applyMarshalPenalty(@PathVariable long raceId,
                                                                 @Valid @RequestBody MarshalPenaltyRequest req) {
        return ResponseEntity.ok(MarshalPenaltyDto.from(refereeService.applyMarshalPenalty(
                raceId, req.entryId(), req.eventId(), req.absenceId(), req.notes(), CurrentOfficial.id())));
    }
}
