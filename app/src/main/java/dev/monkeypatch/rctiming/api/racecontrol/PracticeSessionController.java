package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
import dev.monkeypatch.rctiming.practice.PracticeTimingService;
import dev.monkeypatch.rctiming.practice.dto.PracticeSessionDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for practice sessions.
 *
 * Endpoints that change a session require the ADMIN or RACE_DIRECTOR role; the reads are open to any
 * signed-in official.
 */
@RestController
@RequestMapping("/api/v1/practice-sessions")
public class PracticeSessionController {

    private final PracticeSessionService sessionService;
    private final PracticeTimingService timingService;
    private final AuditService audit;

    public PracticeSessionController(PracticeSessionService sessionService,
                                     PracticeTimingService timingService,
                                     AuditService audit) {
        this.sessionService = sessionService;
        this.timingService = timingService;
        this.audit = audit;
    }

    /** Create a new practice session in IDLE state. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> create(
            @RequestBody PracticeSessionService.CreateRequest request,
            Authentication auth) {
        PracticeSessionDto session = sessionService.create(CurrentOfficial.actor(auth), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /** Get a practice session by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PracticeSessionDto> getById(@PathVariable Long id) {
        return sessionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** List recent practice sessions (default 10). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PracticeSessionDto> listRecent(
            @RequestParam(defaultValue = "10") int limit) {
        return sessionService.findRecent(limit);
    }

    /** Start a practice session (IDLE → RUNNING). Returns 409 on invalid state. */
    @Audited("audit_log")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> start(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.start(CurrentOfficial.actor(auth), id));
    }

    /** Stop a practice session (RUNNING → STOPPED). Returns 409 on invalid state. */
    @Audited("audit_log")
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> stop(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.stop(CurrentOfficial.actor(auth), id));
    }

    /** Get live timing snapshot (rows sorted by position). */
    @GetMapping("/{id}/snapshot")
    @PreAuthorize("isAuthenticated()")
    public List<PracticeTimingRowDto> getSnapshot(@PathVariable Long id) {
        return timingService.getSnapshot(id);
    }

    /** Get final results for a stopped session (same data, named for clarity). */
    @GetMapping("/{id}/results")
    @PreAuthorize("isAuthenticated()")
    public List<PracticeTimingRowDto> getResults(@PathVariable Long id) {
        return timingService.getSnapshot(id);
    }

    /** Link an unknown transponder to a user in an active session. */
    @Audited("audit_log")
    @PostMapping("/{id}/link-transponder")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<Void> linkTransponder(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody LinkRequest request) {
        timingService.linkTransponder(id, request.transponderNumber(), request.userId(), request.racerName());
        // The link lives in memory only, so there is no change to commit with: the row is written on its own
        audit.entry(CurrentOfficial.actor(auth), "PRACTICE_TRANSPONDER_LINKED").entity("practice_session", id)
                .summary("Linked transponder " + request.transponderNumber() + " to "
                        + (request.racerName() != null ? request.racerName() : "user " + request.userId())
                        + " in practice session " + id)
                .after(request).recordStandalone();
        return ResponseEntity.ok().build();
    }

    /** Request body for linking a transponder to a user. */
    public record LinkRequest(String transponderNumber, Long userId, String racerName) {}
}
