package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.practice.PracticeDayService;
import dev.monkeypatch.rctiming.practice.PracticeDriverService;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
import dev.monkeypatch.rctiming.practice.PracticeTimingService;
import dev.monkeypatch.rctiming.practice.dto.PracticeDayDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeDriverDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeSessionDto;
import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for practice sessions, under {@code /api/v1/practice-sessions}.
 *
 * Endpoints that change a session require the ADMIN or RACE_DIRECTOR role; the reads are open to any
 * signed-in official. Changes are recorded in the audit log. A session is IDLE, RUNNING, then STOPPED.
 */
@RestController
@RequestMapping("/api/v1/practice-sessions")
public class PracticeSessionController {

    private final PracticeSessionService sessionService;
    private final PracticeTimingService timingService;
    private final PracticeDriverService driverService;
    private final PracticeDayService dayService;

    public PracticeSessionController(PracticeSessionService sessionService,
                                     PracticeTimingService timingService,
                                     PracticeDriverService driverService,
                                     PracticeDayService dayService) {
        this.sessionService = sessionService;
        this.timingService = timingService;
        this.driverService = driverService;
        this.dayService = dayService;
    }

    /**
     * Create a new practice session in IDLE state. {@code POST /api/v1/practice-sessions}; ADMIN or RACE_DIRECTOR.
     * Answers 201 with the session. Audited.
     */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> create(
            @RequestBody @Valid PracticeSessionService.CreateRequest request) {
        PracticeSessionDto session = sessionService.create(CurrentOfficial.actor(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * The day's practice, each driver added up across every session that started on the date (today when omitted),
     * averaging the best {@code bestLapN} laps of a run (5 when omitted). {@code GET /day}; any official. The date is
     * read in the club's time zone.
     */
    @GetMapping("/day")
    @PreAuthorize("isAuthenticated()")
    public PracticeDayDto day(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer bestLapN) {
        return dayService.day(date, bestLapN);
    }

    /** Get a practice session by ID. {@code GET /{id}}; any official; 404 if there is none. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PracticeSessionDto> getById(@PathVariable Long id) {
        return sessionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** List recent practice sessions, newest first (default 10). {@code GET}; any official. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PracticeSessionDto> listRecent(
            @RequestParam(defaultValue = "10") int limit) {
        return sessionService.findRecent(limit);
    }

    /**
     * Start a practice session (IDLE → RUNNING). {@code POST /{id}/start}; ADMIN or RACE_DIRECTOR. Returns 409 on
     * invalid state, and also while a race is running, since practice and racing share the one decoder. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> start(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.start(CurrentOfficial.actor(), id));
    }

    /**
     * Stop a practice session (RUNNING → STOPPED). {@code POST /{id}/stop}; ADMIN or RACE_DIRECTOR. Returns 409 on
     * invalid state. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<PracticeSessionDto> stop(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.stop(CurrentOfficial.actor(), id));
    }

    /** Get live timing snapshot (rows sorted by position). {@code GET /{id}/snapshot}; any official. */
    @GetMapping("/{id}/snapshot")
    @PreAuthorize("isAuthenticated()")
    public List<PracticeTimingRowDto> getSnapshot(@PathVariable Long id) {
        return timingService.getSnapshot(id);
    }

    /**
     * Get final results for a stopped session (same data, named for clarity). {@code GET /{id}/results}; any
     * official.
     */
    @GetMapping("/{id}/results")
    @PreAuthorize("isAuthenticated()")
    public List<PracticeTimingRowDto> getResults(@PathVariable Long id) {
        return timingService.getSnapshot(id);
    }

    /**
     * Name a transponder in a session: an existing competitor, or a new walk-in made from a name. A name that matches
     * someone already there answers 409 with the matches unless the request confirms it is a different person.
     * {@code POST /{id}/link-transponder}; ADMIN or RACE_DIRECTOR. 400 for a missing or over-long transponder number;
     * 404 if the session does not exist. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/link-transponder")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public PracticeDriverDto linkTransponder(@PathVariable Long id,
            @RequestBody PracticeDriverService.LinkRequest request) {
        return driverService.link(CurrentOfficial.actor(), id, request);
    }

    /** The transponders named in a session. {@code GET /{id}/drivers}; any official. */
    @GetMapping("/{id}/drivers")
    @PreAuthorize("isAuthenticated()")
    public List<PracticeDriverDto> drivers(@PathVariable Long id) {
        return driverService.list(id);
    }
}
