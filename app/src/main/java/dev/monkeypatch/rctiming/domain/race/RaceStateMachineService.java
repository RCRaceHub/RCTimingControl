package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.service.AutoFinalsSeedingService;
import dev.monkeypatch.rctiming.service.BumpUpSeedingService;
import dev.monkeypatch.rctiming.service.ResultSnapshotService;
import dev.monkeypatch.rctiming.service.RoundGeneratorService;
import dev.monkeypatch.rctiming.service.ShikabaneGridService;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Moves a race through its lifecycle, {@code PENDING -> GRID -> RUNNING -> STOPPED -> RUNNING} or to {@code FINISHED},
 * from one table of allowed moves. Each command checks its move, sets the times it owns, saves the race, publishes a
 * {@link RaceStatusChangedEvent} and has the live timing hub broadcast the new state; finishing also stores the result
 * snapshot and applies the finishing order to the next round and any bump-up promotion. An invalid move throws {@link
 * dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException} (HTTP 409).
 *
 * The commands change and save the race they are given but do not open a transaction themselves: they join the
 * caller's.
 */
@Service
public class RaceStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(RaceStateMachineService.class);

    private static final Map<RaceStatus, Set<RaceStatus>> VALID_TRANSITIONS;

    static {
        Map<RaceStatus, Set<RaceStatus>> m = new EnumMap<>(RaceStatus.class);
        m.put(RaceStatus.PENDING,  EnumSet.of(RaceStatus.GRID));
        m.put(RaceStatus.GRID,     EnumSet.of(RaceStatus.RUNNING, RaceStatus.PENDING));
        m.put(RaceStatus.RUNNING,  EnumSet.of(RaceStatus.STOPPED, RaceStatus.FINISHED));
        m.put(RaceStatus.STOPPED,  EnumSet.of(RaceStatus.RUNNING, RaceStatus.FINISHED));
        m.put(RaceStatus.FINISHED, EnumSet.noneOf(RaceStatus.class));
        VALID_TRANSITIONS = Map.copyOf(m);
    }

    /** The states an abandon is allowed from: any that is not already finished. */
    private static final Set<RaceStatus> ABANDONABLE =
            EnumSet.of(RaceStatus.PENDING, RaceStatus.GRID, RaceStatus.RUNNING, RaceStatus.STOPPED);

    private final LiveTimingHub liveTimingHub;
    private final RoundGeneratorService roundGeneratorService;
    private final RaceRepository raceRepository;
    private final LapTimingService lapTimingService;
    private final RoundRepository roundRepository;
    private final ResultSnapshotService resultSnapshotService;
    private final ApplicationEventPublisher eventPublisher;
    private final BumpUpSeedingService bumpUpSeedingService;
    private final AutoFinalsSeedingService autoFinalsSeedingService;
    private final RaceFormatService raceFormatService;
    private final EventClassRepository eventClassRepository;
    private final RaceEliminationRepository raceEliminationRepository;
    private final RacePointPenaltyRepository racePointPenaltyRepository;
    private final ShikabaneGridService shikabaneGridService;

    public RaceStateMachineService(LiveTimingHub liveTimingHub,
                                   RoundGeneratorService roundGeneratorService,
                                   RaceRepository raceRepository,
                                   LapTimingService lapTimingService,
                                   RoundRepository roundRepository,
                                   ResultSnapshotService resultSnapshotService,
                                   ApplicationEventPublisher eventPublisher,
                                   BumpUpSeedingService bumpUpSeedingService,
                                   AutoFinalsSeedingService autoFinalsSeedingService,
                                   RaceFormatService raceFormatService,
                                   EventClassRepository eventClassRepository,
                                   RaceEliminationRepository raceEliminationRepository,
                                   RacePointPenaltyRepository racePointPenaltyRepository,
                                   ShikabaneGridService shikabaneGridService) {
        this.liveTimingHub = liveTimingHub;
        this.roundGeneratorService = roundGeneratorService;
        this.raceRepository = raceRepository;
        this.lapTimingService = lapTimingService;
        this.roundRepository = roundRepository;
        this.resultSnapshotService = resultSnapshotService;
        this.eventPublisher = eventPublisher;
        this.bumpUpSeedingService = bumpUpSeedingService;
        this.autoFinalsSeedingService = autoFinalsSeedingService;
        this.raceFormatService = raceFormatService;
        this.eventClassRepository = eventClassRepository;
        this.raceEliminationRepository = raceEliminationRepository;
        this.racePointPenaltyRepository = racePointPenaltyRepository;
        this.shikabaneGridService = shikabaneGridService;
    }

    // start, finish and abandon set the race's times before the transition saves it, so every
    // listener reads them.

    /**
     * Calls the race to the grid. A final cannot be called until every qualifying heat of its class has finished, and
     * the start type is refreshed from the class's current format first.
     */
    public void callGrid(Race race) {
        requireAllowed(race, RaceStatus.GRID);
        requireQualifyingComplete(race);
        refreshStartType(race);
        transition(race, RaceStatus.GRID);
    }

    /**
     * The race starts the way its class's format says now, not the way it did when the rounds were generated, so a
     * format change made since then applies once the grid is called.
     */
    private void refreshStartType(Race race) {
        Round round = roundRepository.findById(race.getRoundId()).orElse(null);
        if (round == null) {
            return;
        }
        EventClass eventClass = race.getEventClassId() == null ? null
                : eventClassRepository.findById(race.getEventClassId()).orElse(null);
        if (eventClass == null) {
            return;
        }
        race.setStartType(raceFormatService.startType(eventClass, round.getType()));
    }

    /** Starts the race, or resumes a stopped one: a resume keeps the first start time, so the clock carries on. */
    public void start(Race race) {
        requireAllowed(race, RaceStatus.RUNNING);
        if (race.getStatus() != RaceStatus.STOPPED) {
            requireQualifyingComplete(race);
        }
        if (race.getStartedAt() == null) {
            race.setStartedAt(Instant.now());
        }
        transition(race, RaceStatus.RUNNING);
    }

    /** Stops a running race; {@link #start} resumes it. */
    public void stop(Race race) {
        transition(race, RaceStatus.STOPPED);
    }

    /**
     * Finishes a running or stopped race and sets its finish time. Stores the result snapshot, and when the last
     * qualifying heat of a class finishes seeds its finals (a failure there is logged and does not fail the finish).
     */
    public void finish(Race race) {
        requireAllowed(race, RaceStatus.FINISHED);
        race.setFinishedAt(Instant.now());
        transition(race, RaceStatus.FINISHED);
    }

    /**
     * Finishes the race and marks it abandoned at the same moment. Unlike a finish, a race that has not started
     * can be abandoned too, so a heat that will never run (no one in it, or no-shows) stops holding up its class's
     * finals.
     */
    public void abandon(Race race) {
        if (!ABANDONABLE.contains(race.getStatus())) {
            throw new IllegalStateTransitionException(
                "Cannot abandon race " + race.getId() + " in state " + race.getStatus());
        }
        Instant now = Instant.now();
        race.setFinishedAt(now);
        race.setAbandonedAt(now);
        move(race, RaceStatus.FINISHED);
    }

    /**
     * Restart a race — resets it to PENDING regardless of current state, clears
     * in-memory timing state, and deletes any persisted result snapshot and Shikabane eliminations and point penalties.
     * Intended for false starts or technical issues requiring a full re-run.
     *
     * <p>Unlike the commands above, this does not go through the transition table, so it works from any
     * state, including FINISHED. It saves the race. The start, finish and abandon times are cleared, so a race that was
     * abandoned and is restarted is no longer marked abandoned.
     */
    public void restart(Race race) {
        race.setStatus(RaceStatus.PENDING);
        race.setStartedAt(null);
        race.setFinishedAt(null);
        race.setAbandonedAt(null);
        raceRepository.save(race);

        AfterCommit.run(() -> lapTimingService.releaseState(race.getId()));
        resultSnapshotService.deleteByRaceId(race.getId());
        raceEliminationRepository.deleteByRaceId(race.getId());
        racePointPenaltyRepository.deleteByRaceId(race.getId());
        liveTimingHub.broadcastStateChange(race.getId(), RaceStatus.PENDING);
    }

    /**
     * Moves the race to {@code target} if the transition table allows it, saves it, and tells
     * everything that reacts. Use the commands above, which also set the race's times.
     */
    void transition(Race race, RaceStatus target) {
        requireAllowed(race, target);
        move(race, target);
    }

    /** The move itself, once the caller has checked it is allowed. */
    private void move(Race race, RaceStatus target) {
        race.setStatus(target);

        // Save before anything reacts, so the result snapshot and the listeners read the race's new
        // status and times from the database
        raceRepository.save(race);

        // Publish domain event for audio/other listeners
        eventPublisher.publishEvent(new RaceStatusChangedEvent(this, race.getId(), target));

        // Broadcast state change over STOMP
        liveTimingHub.broadcastStateChange(race.getId(), target);

        // On RUNNING/STOPPED → FINISHED: propagate finishing order, then persist result snapshot
        if (target == RaceStatus.FINISHED) {
            // For a Shikabane heat this is replaced by ShikabaneGridService below
            applyFinishingOrderToNextRace(race);
            resultSnapshotService.snapshot(race.getId());
            // A Shikabane main is promoted from its stored result, which puts the dead cars behind the survivors
            if (isShikabane(race) && isFinal(race)) {
                applyBumpUpPromotion(race, resultSnapshotService.finishingOrder(race.getId()));
            }
            // After the snapshot too: a Shikabane heat's next grid is drawn from the stored result and its points
            shikabaneGridService.applyAfterFinish(race);
            // After the snapshot, since the finals are seeded from the qualifying results stored with it
            seedFinalsAfterQualifying(race);
        }
    }

    /**
     * A final can't be called to the grid or started until every qualifying heat for its class is finished
     * (an abandoned heat counts as finished). Other races are not held up.
     */
    private void requireQualifyingComplete(Race race) {
        Round round = roundRepository.findById(race.getRoundId()).orElse(null);
        if (round == null || round.getType() != RoundType.FINAL) {
            return;
        }
        long unfinished = raceRepository.findByEventClassIdAndRoundType(race.getEventClassId(), RoundType.QUALIFIER)
                .stream().filter(q -> q.getStatus() != RaceStatus.FINISHED).count();
        if (unfinished > 0) {
            throw new IllegalStateTransitionException(
                "Cannot call the grid for or start a final until all qualifying heats for its class are finished ("
                + unfinished + " still to run)");
        }
    }

    /** When the last qualifying heat of a class finishes, its finals are seeded from the qualifying results. */
    private void seedFinalsAfterQualifying(Race finishedRace) {
        Round round = roundRepository.findById(finishedRace.getRoundId()).orElse(null);
        if (round != null && round.getType() == RoundType.QUALIFIER) {
            // Not allowed to fail the finish: the official can still seed the finals by hand
            try {
                autoFinalsSeedingService.seedIfQualifyingComplete(finishedRace.getEventClassId());
            } catch (RuntimeException e) {
                log.warn("Could not seed the finals of event class {} after race {} finished",
                        finishedRace.getEventClassId(), finishedRace.getId(), e);
            }
        }
    }

    private static void requireAllowed(Race race, RaceStatus target) {
        if (!VALID_TRANSITIONS.getOrDefault(race.getStatus(), Set.of()).contains(target)) {
            throw new IllegalStateTransitionException(
                "Cannot transition race " + race.getId()
                + " from " + race.getStatus() + " to " + target);
        }
    }

    /**
     * When a race finishes, look up the next race in the same heat across the next round
     * for PRACTICE/QUALIFIER rounds, and apply the finishing order as the starting grid.
     *
     * Bump-up finals are handled separately by BumpUpSeedingService — not here.
     */
    private void applyFinishingOrderToNextRace(Race finishedRace) {
        // Only apply for PRACTICE and QUALIFIER rounds
        Round finishedRound = roundRepository.findById(finishedRace.getRoundId()).orElse(null);
        if (finishedRound == null) {
            return;
        }

        // Handle FINAL rounds: apply bump-up promotion to the next-higher final
        if (finishedRound.getType() == RoundType.FINAL) {
            // A Shikabane main is promoted from its stored result instead, after the snapshot (see move)
            if (!isShikabane(finishedRace)) {
                applyBumpUpPromotion(finishedRace, null);
            }
            return;
        }

        // Only continue for PRACTICE and QUALIFIER rounds
        if (finishedRound.getType() != RoundType.PRACTICE && finishedRound.getType() != RoundType.QUALIFIER) {
            return;
        }

        // Find the next round in the same event (next sequenceInEvent)
        List<Round> allRounds = roundRepository.findByEventIdOrderBySequenceInEvent(finishedRound.getEventId());
        Round nextRound = null;
        for (Round r : allRounds) {
            if (r.getSequenceInEvent() > finishedRound.getSequenceInEvent()) {
                nextRound = r;
                break;
            }
        }
        if (nextRound == null) {
            return;
        }
        // Practice does not set the qualifying order: qualifying starts in car number order
        if (finishedRound.getType() == RoundType.PRACTICE && nextRound.getType() == RoundType.QUALIFIER) {
            return;
        }

        // Find the race in the next round with the same heatNumber and eventClassId
        List<Race> nextRoundRaces = raceRepository.findByRoundIdOrderBySequenceInRound(nextRound.getId());
        Optional<Race> nextRace = nextRoundRaces.stream()
                .filter(r -> r.getHeatNumber() == finishedRace.getHeatNumber()
                          && r.getEventClassId().equals(finishedRace.getEventClassId()))
                .findFirst();

        if (nextRace.isEmpty()) {
            return;
        }

        // Get finishing order from in-memory state
        Optional<LiveRaceState> state = lapTimingService.peek(finishedRace.getId());
        if (state.isEmpty()) {
            log.info("Race {} finished with no in-memory state — skipping finishing-order propagation", finishedRace.getId());
            return;
        }

        List<Long> finishingEntryIds = state.get().calculatePositions().stream()
                .map(LiveTimingRowDto::entryId)
                .toList();

        if (finishingEntryIds.isEmpty()) {
            return;
        }

        // Not caught: the service joins the finish's transaction, so a failure there already marks it for
        // rollback, and catching it would only turn the error into a failed commit (#217)
        roundGeneratorService.applyPreviousRoundFinishingOrder(nextRace.get().getId(), finishingEntryIds);
        log.info("Applied finishing order from race {} to next race {}", finishedRace.getId(), nextRace.get().getId());
    }

    /**
     * When a lower final (B, C, ...) finishes, apply bump-up promotion to fill
     * the bump slots in the next-higher final. Broadcasts a STOMP alert after promotion
     * so the race director knows before starting the next final.
     *
     * A-Finals are skipped (no higher final above A).
     */
    private boolean isShikabane(Race race) {
        return raceFormatService.shikabaneConfig(eventClassRepository.findById(race.getEventClassId()).orElse(null))
                .isPresent();
    }

    private boolean isFinal(Race race) {
        return roundRepository.findById(race.getRoundId()).map(r -> r.getType() == RoundType.FINAL).orElse(false);
    }

    /**
     * Promotes from a finished lower final. {@code storedOrder} is the stored result's entry ids, best first, or
     * null to take the order from the live timing state (every format but Shikabane).
     */
    private void applyBumpUpPromotion(Race finishedFinalRace, List<Long> storedOrder) {
        String letter = finishedFinalRace.getFinalLetter();
        if (letter == null || letter.isEmpty() || letter.charAt(0) <= 'A') {
            // A-Final or no letter — nothing to promote
            return;
        }

        List<Long> finishers = storedOrder;
        if (finishers == null) {
            Optional<LiveRaceState> state = lapTimingService.peek(finishedFinalRace.getId());
            if (state.isEmpty()) {
                log.warn("Bump-up: no live state for finished final race {}", finishedFinalRace.getId());
                return;
            }
            finishers = state.get().calculatePositions().stream().map(LiveTimingRowDto::entryId).toList();
        }

        // Not caught, as above; with no higher final it returns no one rather than throwing
        List<Long> promoted = bumpUpSeedingService.applyBumpUpResults(finishedFinalRace.getId(), finishers);
        log.info("Bump-up: promoted {} from {}-final race {}", promoted, letter, finishedFinalRace.getId());
        if (!promoted.isEmpty()) {
            liveTimingHub.broadcastBumpUpAlert(finishedFinalRace.getId(), promoted);
        }
    }
}
