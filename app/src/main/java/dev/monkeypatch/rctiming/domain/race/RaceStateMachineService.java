package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.service.BumpUpSeedingService;
import dev.monkeypatch.rctiming.service.ResultSnapshotService;
import dev.monkeypatch.rctiming.service.RoundGeneratorService;
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

    private final LiveTimingHub liveTimingHub;
    private final RoundGeneratorService roundGeneratorService;
    private final RaceRepository raceRepository;
    private final LapTimingService lapTimingService;
    private final RoundRepository roundRepository;
    private final ResultSnapshotService resultSnapshotService;
    private final ApplicationEventPublisher eventPublisher;
    private final BumpUpSeedingService bumpUpSeedingService;

    public RaceStateMachineService(LiveTimingHub liveTimingHub,
                                   RoundGeneratorService roundGeneratorService,
                                   RaceRepository raceRepository,
                                   LapTimingService lapTimingService,
                                   RoundRepository roundRepository,
                                   ResultSnapshotService resultSnapshotService,
                                   ApplicationEventPublisher eventPublisher,
                                   BumpUpSeedingService bumpUpSeedingService) {
        this.liveTimingHub = liveTimingHub;
        this.roundGeneratorService = roundGeneratorService;
        this.raceRepository = raceRepository;
        this.lapTimingService = lapTimingService;
        this.roundRepository = roundRepository;
        this.resultSnapshotService = resultSnapshotService;
        this.eventPublisher = eventPublisher;
        this.bumpUpSeedingService = bumpUpSeedingService;
    }

    // start, finish and abandon set the race's times before the transition saves it, so every
    // listener reads them.

    public void callGrid(Race race) {
        transition(race, RaceStatus.GRID);
    }

    /** Starts the race, or resumes a stopped one: a resume keeps the first start time, so the clock carries on. */
    public void start(Race race) {
        requireAllowed(race, RaceStatus.RUNNING);
        if (race.getStartedAt() == null) {
            race.setStartedAt(Instant.now());
        }
        transition(race, RaceStatus.RUNNING);
    }

    public void stop(Race race) {
        transition(race, RaceStatus.STOPPED);
    }

    public void finish(Race race) {
        requireAllowed(race, RaceStatus.FINISHED);
        race.setFinishedAt(Instant.now());
        transition(race, RaceStatus.FINISHED);
    }

    /** Finishes the race and marks it abandoned at the same moment. */
    public void abandon(Race race) {
        requireAllowed(race, RaceStatus.FINISHED);
        Instant now = Instant.now();
        race.setFinishedAt(now);
        race.setAbandonedAt(now);
        transition(race, RaceStatus.FINISHED);
    }

    /**
     * Restart a race — resets it to PENDING regardless of current state, clears
     * in-memory timing state, and deletes any persisted result snapshot.
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

        lapTimingService.releaseState(race.getId());
        resultSnapshotService.deleteByRaceId(race.getId());
        liveTimingHub.broadcastStateChange(race.getId(), RaceStatus.PENDING);
    }

    /**
     * Moves the race to {@code target} if the transition table allows it, saves it, and tells
     * everything that reacts. Use the commands above, which also set the race's times.
     */
    void transition(Race race, RaceStatus target) {
        requireAllowed(race, target);
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
            applyFinishingOrderToNextRace(race);
            resultSnapshotService.snapshot(race.getId());
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
            applyBumpUpPromotion(finishedRace);
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
    private void applyBumpUpPromotion(Race finishedFinalRace) {
        String letter = finishedFinalRace.getFinalLetter();
        if (letter == null || letter.isEmpty() || letter.charAt(0) <= 'A') {
            // A-Final or no letter — nothing to promote
            return;
        }

        Optional<LiveRaceState> state = lapTimingService.peek(finishedFinalRace.getId());
        if (state.isEmpty()) {
            log.warn("Bump-up: no live state for finished final race {}", finishedFinalRace.getId());
            return;
        }

        List<Long> finishers = state.get().calculatePositions().stream()
                .map(LiveTimingRowDto::entryId)
                .toList();

        // Not caught, as above; with no higher final it returns no one rather than throwing
        List<Long> promoted = bumpUpSeedingService.applyBumpUpResults(finishedFinalRace.getId(), finishers);
        log.info("Bump-up: promoted {} from {}-final race {}", promoted, letter, finishedFinalRace.getId());
        if (!promoted.isEmpty()) {
            liveTimingHub.broadcastBumpUpAlert(finishedFinalRace.getId(), promoted);
        }
    }
}
