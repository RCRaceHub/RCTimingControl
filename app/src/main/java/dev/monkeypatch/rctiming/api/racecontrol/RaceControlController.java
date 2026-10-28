package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalAdjustmentRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MoveEntryRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.MoveFinalRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.SkipToRaceRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.query.racecontrol.EventResultsDto;
import dev.monkeypatch.rctiming.query.racecontrol.EventResultsQuery;
import dev.monkeypatch.rctiming.query.racecontrol.QualifyingTableDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import dev.monkeypatch.rctiming.security.AnyOfficial;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.api.racecontrol.dto.CalculateAllFinalsRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.CalculateFinalsRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.FinalsMoveRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.FinalsSwapRequest;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalsLineupDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalsReviewDto;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import dev.monkeypatch.rctiming.service.AutoFinalsSeedingService;
import dev.monkeypatch.rctiming.service.FinalLegsService;
import dev.monkeypatch.rctiming.service.FinalsLineupService;
import dev.monkeypatch.rctiming.service.FinalsRunOrderService;
import dev.monkeypatch.rctiming.service.HeatMoveService;
import dev.monkeypatch.rctiming.service.MarshalService;
import dev.monkeypatch.rctiming.service.RaceControlService;
import dev.monkeypatch.rctiming.service.QualifyingTableService;
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
 * Race control REST API (CTRL-01, CTRL-03, CTRL-08, CTRL-09) and the run order, under
 * {@code /api/v1/race-control}. Endpoints require the RACE_DIRECTOR or ADMIN role; the run order also allows
 * REFEREE.
 *
 * <p>The controller checks authority, binds the request and calls one service method; {@link RaceControlService}
 * owns each lifecycle command's transaction. It calls one method of the race state machine, which checks the move
 * against its table of allowed transitions: a command the race's current state does not allow is a 409 (via
 * {@code IllegalStateTransitionException}), and an unknown race id is a 404. The state machine broadcasts the new
 * state to {@code /topic/race/{raceId}/state} and publishes a {@code RaceStatusChangedEvent}.
 *
 * <p>Every change to a race's lifecycle (call grid, start, stop, finish, abandon, restart) is recorded in the audit
 * log with who did it, in the same transaction as the change (#139).
 */
@RestController
@RequestMapping("/api/v1/race-control")
@DirectorOrAdmin
public class RaceControlController {

    private final RunOrderQuery runOrderQuery;
    private final EventResultsQuery eventResultsQuery;
    private final RaceControlService raceControlService;
    private final MarshalService marshalService;
    private final QualifyingTableService qualifyingTableService;
    private final FinalLegsService finalLegsService;
    private final AutoFinalsSeedingService finalsSeedingService;
    private final HeatMoveService heatMoveService;
    private final FinalsRunOrderService finalsRunOrderService;
    private final FinalsLineupService finalsLineupService;

    public RaceControlController(RunOrderQuery runOrderQuery,
                                  EventResultsQuery eventResultsQuery,
                                  RaceControlService raceControlService,
                                  MarshalService marshalService,
                                  QualifyingTableService qualifyingTableService,
                                  FinalLegsService finalLegsService,
                                  AutoFinalsSeedingService finalsSeedingService,
                                  HeatMoveService heatMoveService,
                                  FinalsRunOrderService finalsRunOrderService,
                                  FinalsLineupService finalsLineupService) {
        this.runOrderQuery = runOrderQuery;
        this.eventResultsQuery = eventResultsQuery;
        this.raceControlService = raceControlService;
        this.marshalService = marshalService;
        this.qualifyingTableService = qualifyingTableService;
        this.finalLegsService = finalLegsService;
        this.finalsSeedingService = finalsSeedingService;
        this.heatMoveService = heatMoveService;
        this.finalsRunOrderService = finalsRunOrderService;
        this.finalsLineupService = finalsLineupService;
    }

    /**
     * The event's races in the order they will be run, with their status.
     * {@code GET /event/{eventId}/run-order}. The referee also reads the run order: it is how the Referee View
     * picks a race (#107). Read only.
     */
    @GetMapping("/event/{eventId}/run-order")
    @RaceControlStaff
    public List<RunOrderItemDto> getRunOrder(@PathVariable long eventId) {
        return runOrderQuery.findForEvent(eventId);
    }

    /**
     * Every finished race of the event with real driver names and each driver's starting grid position, for the
     * round summary, final results and meeting pack printouts. {@code GET /event/{eventId}/results}, any signed-in
     * official. Read only. 404 if there is no such event.
     */
    @GetMapping("/event/{eventId}/results")
    @AnyOfficial
    public EventResultsDto getEventResults(@PathVariable long eventId) {
        return eventResultsQuery.forEvent(eventId);
    }

    /**
     * One finished race with real driver names and each driver's starting grid position, for the heat result
     * printout. {@code GET /race/{raceId}/result}, any signed-in official. Read only. 404 if there is no such race
     * or it has no stored result.
     */
    @GetMapping("/race/{raceId}/result")
    @AnyOfficial
    public EventResultsDto getRaceResult(@PathVariable long raceId) {
        return eventResultsQuery.forRace(raceId);
    }

    /**
     * The combined result of each class's A final that is run in legs (best legs added, lowest wins).
     * {@code GET /event/{eventId}/final-legs}, race director, referee or admin. Read only; empty when no class has legs.
     */
    @GetMapping("/event/{eventId}/final-legs")
    @RaceControlStaff
    public List<FinalLegsDto> getFinalLegs(@PathVariable long eventId) {
        return finalLegsService.forEvent(eventId);
    }

    /**
     * Each class's overall qualifying table: the order its finals are seeded from.
     * {@code GET /event/{eventId}/qualifying}, race director, referee or admin (the meeting pack printout reads it).
     * Read only.
     */
    @GetMapping("/event/{eventId}/qualifying")
    @RaceControlStaff
    public List<QualifyingTableDto> getQualifying(@PathVariable long eventId) {
        return qualifyingTableService.forEvent(eventId);
    }

    /**
     * Calculates the class's finals from its qualifying results: the first time, or again after a heat was re-run or
     * corrected, which replaces any hand edits. Refused once a final has started.
     *
     * <p>{@code POST /event/{eventId}/qualifying/{eventClassId}/seed-finals}; the body is optional and names the cars
     * per final. 204 on success. 404 if the class is not in that event; 409 if a qualifying heat is unfinished (or
     * all were abandoned), the class has no finals yet, or a final has started. Recorded in the audit log.
     */
    @Audited("audit_log")
    @PostMapping("/event/{eventId}/qualifying/{eventClassId}/seed-finals")
    public ResponseEntity<Void> seedFinals(@PathVariable long eventId, @PathVariable long eventClassId,
                                           @Valid @RequestBody(required = false) CalculateFinalsRequest request) {
        finalsSeedingService.reseed(CurrentOfficial.actor(), eventId, eventClassId,
                request == null ? null : request.carsPerFinal());
        return ResponseEntity.noContent().build();
    }

    /**
     * Calculates the finals of every class of the event that is ready, once qualifying is done. Classes that are not
     * ready (qualifying unfinished, a final started, no finals) are left alone and listed in the answer.
     *
     * <p>{@code POST /event/{eventId}/seed-all-finals}; optional body {@code {"carsPerFinal": {"<eventClassId>": n}}} for the classes the
     * official set a number for (ids not in the event are ignored; 400 outside 1 to 100). 200 with {@code calculated}
     * (event class ids) and {@code skipped} (event class id to reason). 404 if the event has no classes. Recorded in the audit log, one
     * entry for each class calculated.
     */
    @Audited("audit_log")
    @PostMapping("/event/{eventId}/seed-all-finals")
    public AutoFinalsSeedingService.CalculateAllResult seedAllFinals(
            @PathVariable long eventId, @Valid @RequestBody(required = false) CalculateAllFinalsRequest request) {
        return finalsSeedingService.reseedAll(CurrentOfficial.actor(), eventId,
                request == null ? null : request.carsPerFinal());
    }

    /**
     * Each class's finals line-up: who is on every final's grid, and whether the finals can be calculated yet.
     * {@code GET /event/{eventId}/finals-lineup}. Read only.
     */
    @GetMapping("/event/{eventId}/finals-lineup")
    @RaceControlStaff
    public List<FinalsLineupDto> getFinalsLineup(@PathVariable long eventId) {
        return finalsLineupService.forEvent(eventId);
    }

    /**
     * Whether the finals line-up screen must be shown instead of the cockpit: qualifying is over, no final has been
     * called and the line-ups are unconfirmed. {@code GET /event/{eventId}/finals-review}. Read only; 404 for an
     * unknown event.
     */
    @GetMapping("/event/{eventId}/finals-review")
    @RaceControlStaff
    public FinalsReviewDto getFinalsReview(@PathVariable long eventId) {
        return finalsLineupService.review(eventId);
    }

    /**
     * Confirms the event's finals line-ups, which brings the cockpit back. {@code POST /event/{eventId}/finals-lineup/confirm};
     * no body. 200 with the new review state. 404 for an unknown event; 409 if the event has no finals, qualifying is
     * not over, a class is still to be calculated or a final has started. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/event/{eventId}/finals-lineup/confirm")
    public FinalsReviewDto confirmFinalsLineup(@PathVariable long eventId) {
        return finalsLineupService.confirm(CurrentOfficial.actor(), eventId);
    }

    /**
     * Moves a driver from a pending final to another pending final of the same class.
     * {@code POST /race/{raceId}/finals/move}; 204 on success. 400 for the same final, different classes, or emptying
     * a final; 404 if the driver is not in the final; 409 if either final has been called or started. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/finals/move")
    public ResponseEntity<Void> moveFinalsDriver(@PathVariable long raceId,
                                                 @Valid @RequestBody FinalsMoveRequest request) {
        finalsLineupService.moveDriver(CurrentOfficial.actor(), raceId, request.entryId(), request.targetRaceId(),
                request.targetPosition());
        return ResponseEntity.noContent().build();
    }

    /**
     * Swaps two drivers' grid places, on one pending final or across two of the same class.
     * {@code POST /race/{raceId}/finals/swap}; 204 on success. 400 for the same driver or different classes; 404 if a
     * driver is not in their final; 409 if either final has been called or started. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/finals/swap")
    public ResponseEntity<Void> swapFinalsDrivers(@PathVariable long raceId,
                                                  @Valid @RequestBody FinalsSwapRequest request) {
        finalsLineupService.swapDrivers(CurrentOfficial.actor(), raceId, request.entryId(), request.otherRaceId(),
                request.otherEntryId());
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
     * Moves a pending final one place earlier or later in the event's run order, so the legs of a final run in legs
     * can sit between other finals. {@code POST /race/{raceId}/move-final}; 204 on success. 404 for an unknown race;
     * 409 if no final of the event runs in legs, the final or the one it swaps with has been called or started or
     * belongs to a Bump Up class, or the move would reverse two legs of one final. Recorded in the audit log.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/move-final")
    public ResponseEntity<Void> moveFinal(@PathVariable long raceId, @Valid @RequestBody MoveFinalRequest request) {
        finalsRunOrderService.move(CurrentOfficial.actor(), raceId, request.direction() == MoveFinalRequest.Direction.UP);
        return ResponseEntity.noContent().build();
    }

    /**
     * Calls the grid: PENDING to GRID. {@code POST /race/{raceId}/call-grid}; 409 from any other state. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/call-grid")
    public ResponseEntity<Void> callGrid(@PathVariable long raceId) {
        raceControlService.callGrid(CurrentOfficial.actor(), raceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Starts the race, or resumes it when it was stopped. {@code POST /race/{raceId}/start}; 409 from any state
     * but GRID and STOPPED. Audited, as {@code RACE_STARTED} or {@code RACE_RESUMED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/start")
    public ResponseEntity<Void> startRace(@PathVariable long raceId) {
        raceControlService.start(CurrentOfficial.actor(), raceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Stops a running race (a red flag): RUNNING to STOPPED. {@code POST /race/{raceId}/stop}; 409 from any other
     * state. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/stop")
    public ResponseEntity<Void> stopRace(@PathVariable long raceId) {
        raceControlService.stop(CurrentOfficial.actor(), raceId);
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
    public ResponseEntity<Void> restartRace(@PathVariable long raceId) {
        raceControlService.restart(CurrentOfficial.actor(), raceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Finishes the race: RUNNING or STOPPED to FINISHED. {@code POST /race/{raceId}/finish}; 409 from any other
     * state. Audited as {@code RACE_FINISHED}.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/finish")
    public ResponseEntity<Void> finishRace(@PathVariable long raceId) {
        raceControlService.finish(CurrentOfficial.actor(), raceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Finishes the race and marks it abandoned. Unlike a restart it keeps the laps so far: the race goes to
     * FINISHED with a result snapshot. It is allowed from any state but FINISHED, so a heat that will never run can be
     * abandoned before it starts. {@code POST /race/{raceId}/abandon}; 409 once the race is FINISHED. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/race/{raceId}/abandon")
    public ResponseEntity<Void> abandonRace(@PathVariable long raceId) {
        raceControlService.abandon(CurrentOfficial.actor(), raceId);
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
    public ResponseEntity<Map<String, Long>> skipTo(@PathVariable long raceId,
                                                     @Valid @RequestBody SkipToRaceRequest req) {
        long eventId = raceControlService.skipTo(raceId, req.targetRaceId());
        return ResponseEntity.ok(Map.of("eventId", eventId, "activeRaceId", req.targetRaceId()));
    }
}
