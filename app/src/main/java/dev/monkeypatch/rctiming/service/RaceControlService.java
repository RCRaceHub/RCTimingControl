package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessDto;
import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessQuery;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.WarmUpCheckService;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What race control does to one race, for {@code RaceControlController} and its neighbours: the lifecycle
 * commands, skipping to another race, and the pre-race readiness and jump start reads.
 *
 * <p>Each lifecycle command is one call of {@link RaceStateMachineService} and the audit row that records it, in one
 * write transaction, so the row exists only if the change committed (#139). Called on the request thread. The
 * state machine broadcasts the new state once the transaction commits. The reads run on the read pool.
 */
@Service
@Transactional
public class RaceControlService {

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final EventClassRepository eventClassRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final RaceStateMachineService stateMachine;
    private final AuditService audit;
    private final LapTimingService lapTimingService;
    private final RaceFormatService raceFormatService;
    private final WarmUpCheckService warmUpCheckService;
    private final PreRaceReadinessQuery preRaceReadinessQuery;

    public RaceControlService(RaceRepository raceRepository,
                              RoundRepository roundRepository,
                              EventClassRepository eventClassRepository,
                              ResultSnapshotRepository resultSnapshotRepository,
                              RaceStateMachineService stateMachine,
                              AuditService audit,
                              LapTimingService lapTimingService,
                              RaceFormatService raceFormatService,
                              WarmUpCheckService warmUpCheckService,
                              PreRaceReadinessQuery preRaceReadinessQuery) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.eventClassRepository = eventClassRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.lapTimingService = lapTimingService;
        this.raceFormatService = raceFormatService;
        this.warmUpCheckService = warmUpCheckService;
        this.preRaceReadinessQuery = preRaceReadinessQuery;
    }

    /**
     * Calls the grid: PENDING to GRID.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     * @throws dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException from any other state (HTTP 409)
     */
    public void callGrid(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.callGrid(race);
        recordLifecycle(actor, race, "RACE_GRID_CALLED", "Called the grid for", before);
    }

    /**
     * Starts the race, or resumes it when it was stopped; audited as {@code RACE_STARTED} or {@code RACE_RESUMED}.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     * @throws dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException from any state but GRID and
     *         STOPPED (HTTP 409)
     */
    public void start(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.start(race);
        // A start from STOPPED is a resume: say so, since the clock carries on
        recordLifecycle(actor, race, before == RaceStatus.STOPPED ? "RACE_RESUMED" : "RACE_STARTED",
                before == RaceStatus.STOPPED ? "Resumed" : "Started", before);
    }

    /**
     * Stops a running race (a red flag): RUNNING to STOPPED.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     * @throws dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException from any other state (HTTP 409)
     */
    public void stop(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.stop(race);
        recordLifecycle(actor, race, "RACE_STOPPED", "Stopped", before);
    }

    /**
     * Resets the race to PENDING from any state, FINISHED included. It clears the race's times and throws away its
     * result snapshot and live timing, so what is lost is written to the audit row first.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     */
    public void restart(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        // Restart throws away the result snapshot and the live timing, so what is lost is written down first
        Map<String, Object> lost = whatARestartDiscards(race);
        stateMachine.restart(race);
        audit.entry(actor, "RACE_RESTARTED")
                .entity("race", race.getId()).race(race.getId()).event(eventIdOf(race))
                .summary("Restarted " + describe(race) + ", discarding its timing and any result")
                .before(lost).after(Map.of("status", RaceStatus.PENDING))
                .record();
    }

    /**
     * Finishes the race: RUNNING or STOPPED to FINISHED.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     * @throws dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException from any other state (HTTP 409)
     */
    public void finish(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.finish(race);
        recordLifecycle(actor, race, "RACE_FINISHED", "Finished", before);
    }

    /**
     * Finishes the race and marks it abandoned, keeping the laps so far.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     * @throws dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException once it is FINISHED (HTTP 409)
     */
    public void abandon(Actor actor, long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceStatus before = race.getStatus();
        stateMachine.abandon(race);
        recordLifecycle(actor, race, "RACE_ABANDONED", "Abandoned", before);
    }

    /**
     * Checks the target race is in the same event as the source race. Changes nothing.
     *
     * @return the event id both races belong to
     * @throws EntityNotFoundException  if either race does not exist (HTTP 404)
     * @throws IllegalArgumentException if the target is in a different event (HTTP 400)
     */
    @ReadTransaction
    public long skipTo(long raceId, long targetRaceId) {
        Race sourceRace = raceRepository.getOrThrow(raceId);
        // Named apart from the race in the path, so a bad target in the body is clear
        Race targetRace = raceRepository.findById(targetRaceId)
                .orElseThrow(() -> new EntityNotFoundException("Target race not found: " + targetRaceId));

        // Both races must belong to the same event (via their rounds)
        long sourceEventId = eventIdOf(sourceRace);
        if (sourceEventId != eventIdOf(targetRace)) {
            throw new IllegalArgumentException("Target race " + targetRaceId + " belongs to a different event");
        }
        return sourceEventId;
    }

    /**
     * The grid call list and marshal duty list for a race, with how it starts (the race's own start type, or the
     * format's when it has none saved), the stagger interval and the entries the decoder has seen on the warm-up lap.
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     */
    @ReadTransaction
    public PreRaceReadinessDto preRaceReadiness(long raceId) {
        PreRaceReadinessDto readiness = preRaceReadinessQuery.load(raceId);
        Race race = raceRepository.getOrThrow(raceId);
        EventClass eventClass = eventClassRepository.findById(race.getEventClassId()).orElse(null);
        // A race made before it kept its start type takes the format's start, as the round generator would
        StartType startType = race.getStartType() != null ? race.getStartType()
                : raceFormatService.startType(eventClass, roundRepository.getOrThrow(race.getRoundId()).getType());
        return readiness.withStart(startType.name(), raceFormatService.staggerIntervalSeconds(eventClass))
                .withSeen(warmUpCheckService.seenEntryIds(raceId));
    }

    /**
     * The jump starts so far, in the order they happened; empty when none, or when the race is not in progress
     * (they are kept in memory with the race's live timing).
     *
     * @throws EntityNotFoundException if there is no such race (HTTP 404)
     */
    @ReadTransaction
    public List<JumpStartDto> jumpStarts(long raceId) {
        raceRepository.requireExists(raceId);
        return lapTimingService.jumpStartsOf(raceId);
    }

    /** Writes the audit row for a state change, in the transaction of the change. */
    private void recordLifecycle(Actor actor, Race race, String action, String verb, RaceStatus before) {
        audit.entry(actor, action)
                .entity("race", race.getId()).race(race.getId()).event(eventIdOf(race))
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

    private long eventIdOf(Race race) {
        Round round = roundRepository.getOrThrow(race.getRoundId());
        return round.getEventId();
    }
}
