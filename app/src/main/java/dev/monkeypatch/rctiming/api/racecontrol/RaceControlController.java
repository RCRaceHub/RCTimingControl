package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalAdjustmentRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MoveEntryRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.SkipToRaceRequest;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.query.racecontrol.EventResultsDto;
import dev.monkeypatch.rctiming.query.racecontrol.EventResultsQuery;
import dev.monkeypatch.rctiming.query.racecontrol.QualifyingTableDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.AutoFinalsSeedingService;
import dev.monkeypatch.rctiming.service.HeatMoveService;
import dev.monkeypatch.rctiming.service.MarshalService;
import dev.monkeypatch.rctiming.service.QualifyingTableService;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Race control REST API (CTRL-01, CTRL-03, CTRL-08, CTRL-09) and the run order, under
 * {@code /api/v1/race-control}. Endpoints require the RACE_DIRECTOR or ADMIN role; the run order also allows
 * REFEREE.
 *
 * <p>The lifecycle commands each call one method of {@link RaceStateMachineService}, which checks the move against
 * its table of allowed transitions: a command the race's current state does not allow is a 409 (via
 * {@code IllegalStateTransitionException}), and an unknown race id is a 404. The state machine broadcasts the new
 * state to {@code /topic/race/{raceId}/state} and publishes a {@code RaceStatusChangedEvent}.
 *
 * <p>Every change to a race's lifecycle (call grid, start, stop, finish, abandon, restart) is recorded in the audit
 * log with who did it, in the same transaction as the change (#139).
 */
@RestController
@RequestMapping("/api/v1/race-control")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
public class RaceControlController {

    private final RunOrderQuery runOrderQuery;
    private final EventResultsQuery eventResultsQuery;
    private final RaceRepository raceRepository;
    private final RaceStateMachineService stateMachine;
    private final MarshalService marshalService;
    private final LapTimingService lapTimingService;
    private final RoundRepository roundRepository;
    private final AuditService audit;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final QualifyingTableService qualifyingTableService;
    private final AutoFinalsSeedingService finalsSeedingService;
    private final HeatMoveService heatMoveService;

    public RaceControlController(RunOrderQuery runOrderQuery,
                                  EventResultsQuery eventResultsQuery,
                                  RaceRepository raceRepository,
                                  RaceStateMachineService stateMachine,
                                  MarshalService marshalService,
                                  LapTimingService lapTimingService,
                                  RoundRepository roundRepository,
                                  AuditService audit,
                                  ResultSnapshotRepository resultSnapshotRepository,
                                  QualifyingTableService qualifyingTableService,
                                  AutoFinalsSeedingService finalsSeedingService,
                                  HeatMoveService heatMoveService) {
        this.runOrderQuery = runOrderQuery;
        this.eventResultsQuery = eventResultsQuery;
        this.raceRepository = raceRepository;
        this.stateMachine = stateMachine;
        this.marshalService = marshalService;
        this.lapTimingService = lapTimingService;
        this.roundRepository = roundRepository;
        this.audit = audit;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.qualifyingTableService = qualifyingTableService;
        this.finalsSeedingService = finalsSeedingService;
        this.heatMoveService = heatMoveService;
    }

    /**
     * The event's races in the order they will be run, with their status.
     * {@code GET /event/{eventId}/run-order}. The referee also reads the run order: it is how the Referee View
     * picks a race (#107). Read only.
     */
    @GetMapping("/event/{eventId}/run-order")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
    public List<RunOrderItemDto> getRunOrder(@PathVariable long eventId) {
        return runOrderQuery.findForEvent(eventId);
    }

    /**
     * Every finished race of the event with real driver names and each driver's starting grid position, for the
     * round summary, final results and meeting pack printouts. {@code GET /event/{eventId}/results}, any signed-in
     * official. Read only. 404 if there is no such event.
     */
    @GetMapping("/event/{eventId}/results")
    @PreAuthorize("isAuthenticated()")
    public EventResultsDto getEventResults(@PathVariable long eventId) {
        return eventResultsQuery.forEvent(eventId);
    }

    /**
     * One finished race with real driver names and each driver's starting grid position, for the heat result
     * printout. {@code GET /race/{raceId}/result}, any signed-in official. Read only. 404 if there is no such race
     * or it has no stored result.
     */
    @GetMapping("/race/{raceId}/result")
    @PreAuthorize("isAuthenticated()")
    public EventResultsDto getRaceResult(@PathVariable long raceId) {
        return eventResultsQuery.forRace(raceId);
    }

    /**
     * Each class's overall qualifying table: the order its finals are seeded from.
     * {@code GET /event/{eventId}/qualifying}, race director, referee or admin (the meeting pack printout reads it).
     * Read only.
     */
    @GetMapping("/event/{eventId}/qualifying")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
    public List<QualifyingTableDto> getQualifying(@PathVariable long eventId) {
        return qualifyingTableService.forEvent(eventId);
    }

    /**
     * Seeds the class's finals again from the qualifying results so far, for after a heat was re-run or corrected.
     * Refused once a final has started.
     *
     * <p>{@code POST /event/{eventId}/qualifying/{eventClassId}/seed-finals}; 204 on success. 404 if the class is
     * not in that event; 409 if a qualifying heat is unfinished (or all were abandoned), the class has no finals
     * yet, or a final has started. Recorded in the audit log.
     */
    @Audited("audit_log")
    @PostMapping("/event/{eventId}/qualifying/{eventClassId}/seed-finals")
    @Transactional
    public ResponseEntity<Void> seedFinals(@PathVariable long eventId, @PathVariable long eventClassId) {
        finalsSeedingService.reseed(CurrentOfficial.actor(), eventId, eventClassId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Moves a driver out of a pending heat into another pending heat of the same class, in the same round or, with
     * {@code allRounds}, in every practice and qualifying round that still has both heats pending.
     *
     * <p>{@code POST /race/{raceId}/move-entry}; 204 on success. 400 if the heats are the same, in different
     * classes or events, finals, or (without {@code allRounds}) in different rounds; 409 if either heat has been
     * called or started. Recorded in the audit log.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/move-entry")
    public ResponseEntity<Void> moveEntry(@PathVariable long raceId, @Valid @RequestBody MoveEntryRequest request) {
        heatMoveService.moveEntry(CurrentOfficial.actor(), raceId, request.entryId(), request.targetRaceId(),
                request.allRounds());
        return ResponseEntity.noContent().build();
    }

    /**
     * Calls the grid: PENDING to GRID. {@code POST /race/{raceId}/call-grid}; 409 from any other state. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/call-grid")
    @Transactional
    public ResponseEntity<Void> callGrid(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.callGrid(race);
        recordLifecycle(race, "RACE_GRID_CALLED", "Called the grid for", before);
        return ResponseEntity.ok().build();
    }

    /**
     * Starts the race, or resumes it when it was stopped. {@code POST /race/{raceId}/start}; 409 from any state
     * but GRID and STOPPED. Audited, as {@code RACE_STARTED} or {@code RACE_RESUMED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/start")
    @Transactional
    public ResponseEntity<Void> startRace(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.start(race);
        // A start from STOPPED is a resume: say so, since the clock carries on
        recordLifecycle(race, before == RaceStatus.STOPPED ? "RACE_RESUMED" : "RACE_STARTED",
                before == RaceStatus.STOPPED ? "Resumed" : "Started", before);
        return ResponseEntity.ok().build();
    }

    /**
     * Stops a running race (a red flag): RUNNING to STOPPED. {@code POST /race/{raceId}/stop}; 409 from any other
     * state. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/stop")
    @Transactional
    public ResponseEntity<Void> stopRace(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.stop(race);
        recordLifecycle(race, "RACE_STOPPED", "Stopped", before);
        return ResponseEntity.ok().build();
    }

    /**
     * Resets the race to PENDING from any state, FINISHED included. It clears the race's start, finish and
     * abandoned times and throws away its result snapshot and live timing, so what is lost is recorded in the audit
     * row first. Use it for a false start, not to correct a result.
     *
     * <p>{@code POST /race/{raceId}/restart}; the state machine does not refuse it, so only a missing race (404)
     * fails it. Only broadcasts the new state. Audited as {@code RACE_RESTARTED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/restart")
    @Transactional
    public ResponseEntity<Void> restartRace(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        // Restart throws away the result snapshot and the live timing, so what is lost is written down first
        Map<String, Object> lost = whatARestartDiscards(race);
        stateMachine.restart(race);
        audit.entry(CurrentOfficial.actor(), "RACE_RESTARTED")
                .entity("race", race.getId()).race(race.getId()).event(resolveEventId(race))
                .summary("Restarted " + describe(race) + ", discarding its timing and any result")
                .before(lost).after(Map.of("status", RaceStatus.PENDING))
                .record();
        return ResponseEntity.ok().build();
    }

    /**
     * Finishes the race: RUNNING or STOPPED to FINISHED. {@code POST /race/{raceId}/finish}; 409 from any other
     * state. Audited as {@code RACE_FINISHED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/finish")
    @Transactional
    public ResponseEntity<Void> finishRace(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.finish(race);
        recordLifecycle(race, "RACE_FINISHED", "Finished", before);
        return ResponseEntity.ok().build();
    }

    /**
     * Finishes the race and marks it abandoned. Unlike a restart it keeps the laps so far: the race goes to
     * FINISHED with a result snapshot. It is allowed from any state but FINISHED, so a heat that will never run can be
     * abandoned before it starts. {@code POST /race/{raceId}/abandon}; 409 once the race is FINISHED. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/abandon")
    @Transactional
    public ResponseEntity<Void> abandonRace(@PathVariable long raceId) {
        Race race = loadRace(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.abandon(race);
        recordLifecycle(race, "RACE_ABANDONED", "Abandoned", before);
        return ResponseEntity.ok().build();
    }

    /**
     * Adds or takes off one lap for a driver by hand (a marshal lap). {@code POST /race/{raceId}/marshal-adjustment};
     * 200 on success. 400 for a lap delta other than +1 or -1. The adjustment is stored with who made it and the
     * race's state at the time; while the race is not finished the live positions are recalculated and rebroadcast,
     * and once it has finished its corrected results are sent again instead. Marshal laps are not state transitions.
     */
    @Audited("marshal_adjustments")
    @PostMapping("/race/{raceId}/marshal-adjustment")
    public ResponseEntity<Void> marshalAdjustment(@PathVariable long raceId,
                                                   @Valid @RequestBody MarshalAdjustmentRequest req) {
        marshalService.adjust(raceId, req.entryId(), req.transponderNumber(), req.lapDelta(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }

    /**
     * Checks the target race is in the same event and answers with it; the client keeps the active-race pointer.
     * {@code POST /race/{raceId}/skip-to}. Changes nothing on the server, so it is not audited. 404 if either race
     * does not exist; 400 if the target is in a different event. The answer holds {@code eventId} and
     * {@code activeRaceId}.
     */
    @PostMapping("/race/{raceId}/skip-to")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> skipTo(@PathVariable long raceId,
                                                     @Valid @RequestBody SkipToRaceRequest req) {
        Race sourceRace = loadRace(raceId);
        // Named apart from the race in the path, so a bad target in the body is clear
        Race targetRace = raceRepository.findById(req.targetRaceId())
                .orElseThrow(() -> new EntityNotFoundException("Target race not found: " + req.targetRaceId()));

        // Validate both races belong to the same event (via their rounds)
        long sourceEventId = resolveEventId(sourceRace);
        long targetEventId = resolveEventId(targetRace);
        if (sourceEventId != targetEventId) {
            throw new IllegalArgumentException(
                "Target race " + req.targetRaceId() + " belongs to a different event");
        }

        return ResponseEntity.ok(Map.of("eventId", sourceEventId, "activeRaceId", req.targetRaceId()));
    }

    /** Writes the audit row for a state change, in the transaction of the change. */
    private void recordLifecycle(Race race, String action, String verb, RaceStatus before) {
        audit.entry(CurrentOfficial.actor(), action)
                .entity("race", race.getId()).race(race.getId()).event(resolveEventId(race))
                .summary(verb + " " + describe(race))
                .before(before).after(race.getStatus())
                .record();
    }

    /** What a restart takes away: the race's times, its stored result, and the timing held in memory. */
    private Map<String, Object> whatARestartDiscards(Race race) {
        Map<String, Object> lost = new LinkedHashMap<>();
        lost.put("status", race.getStatus());
        lost.put("startedAt", race.getStartedAt());
        lost.put("finishedAt", race.getFinishedAt());
        lost.put("abandonedAt", race.getAbandonedAt());
        resultSnapshotRepository.findByRaceId(race.getId()).ifPresent(snapshot -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", snapshot.getId());
            result.put("finishedAt", snapshot.getFinishedAt());
            result.put("positions", snapshot.getPositionsJson());
            lost.put("resultSnapshot", result);
        });
        lapTimingService.peek(race.getId()).ifPresent(state -> lost.put("livePositions", state.calculatePositions()));
        return lost;
    }

    /** The race as an official knows it, such as {@code A final (race 42)} or {@code heat 2 (race 42)}. */
    private static String describe(Race race) {
        String what = race.getFinalLetter() != null
                ? ("Z".equals(race.getFinalLetter()) ? "zombie round" : race.getFinalLetter() + " final")
                : "heat " + race.getHeatNumber();
        return what + " (race " + race.getId() + ")";
    }

    private Race loadRace(long raceId) {
        return raceRepository.getOrThrow(raceId);
    }

    private long resolveEventId(Race race) {
        Round round = roundRepository.getOrThrow(race.getRoundId());
        return round.getEventId();
    }
}
