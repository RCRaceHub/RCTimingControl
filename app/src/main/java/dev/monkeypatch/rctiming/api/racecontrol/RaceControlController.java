package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.MarshalAdjustmentRequest;
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
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.service.MarshalService;
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
 * Race control REST API (CTRL-01, CTRL-03, CTRL-08, CTRL-09) and the run order. Endpoints require the
 * RACE_DIRECTOR or ADMIN role; the run order also allows REFEREE.
 *
 * <p>Every change to a race's lifecycle (call grid, start, stop, finish, abandon, restart) is recorded in the audit
 * log with who did it, in the same transaction as the change (#139).
 */
@RestController
@RequestMapping("/api/v1/race-control")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','ADMIN')")
public class RaceControlController {

    private final RunOrderQuery runOrderQuery;
    private final RaceRepository raceRepository;
    private final RaceStateMachineService stateMachine;
    private final MarshalService marshalService;
    private final LapTimingService lapTimingService;
    private final RoundRepository roundRepository;
    private final AuditService audit;
    private final ResultSnapshotRepository resultSnapshotRepository;

    public RaceControlController(RunOrderQuery runOrderQuery,
                                  RaceRepository raceRepository,
                                  RaceStateMachineService stateMachine,
                                  MarshalService marshalService,
                                  LapTimingService lapTimingService,
                                  RoundRepository roundRepository,
                                  AuditService audit,
                                  ResultSnapshotRepository resultSnapshotRepository) {
        this.runOrderQuery = runOrderQuery;
        this.raceRepository = raceRepository;
        this.stateMachine = stateMachine;
        this.marshalService = marshalService;
        this.lapTimingService = lapTimingService;
        this.roundRepository = roundRepository;
        this.audit = audit;
        this.resultSnapshotRepository = resultSnapshotRepository;
    }

    /** The referee also reads the run order: it is how the Referee View picks a race (#107). */
    @GetMapping("/event/{eventId}/run-order")
    @PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
    public List<RunOrderItemDto> getRunOrder(@PathVariable long eventId) {
        return runOrderQuery.findForEvent(eventId);
    }

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

    /** Resets the race to PENDING from any state. */
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

    @Audited("marshal_adjustments")
    @PostMapping("/race/{raceId}/marshal-adjustment")
    public ResponseEntity<Void> marshalAdjustment(@PathVariable long raceId,
                                                   @Valid @RequestBody MarshalAdjustmentRequest req) {
        marshalService.adjust(raceId, req.entryId(), req.transponderNumber(), req.lapDelta(), CurrentOfficial.id());
        return ResponseEntity.ok().build();
    }

    /** Checks the target race is in the same event and answers with it; the client keeps the active-race pointer. */
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
                ? race.getFinalLetter() + " final"
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
