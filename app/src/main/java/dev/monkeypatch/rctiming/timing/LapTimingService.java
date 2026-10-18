package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.NameVisibilityChangedEvent;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory race state manager for live timing.
 * Holds a {@code ConcurrentHashMap<Long, LiveRaceState>} keyed by raceId.
 * Positions are calculated in memory and broadcast over STOMP — never persisted during a race.
 *
 * <p><strong>Threading.</strong> Decoder passings reach {@link #onLapPassing} synchronously on the
 * single timing thread ({@code timingExecutor}), so they are handled one at a time in decoder order and
 * broadcasts go out in that order. REST requests (marshal laps, lap penalties, transponder links) call in from
 * request threads; {@link LiveRaceState}'s own methods are synchronized, which is what keeps those changes
 * apart from the timing thread's.
 *
 * <p>Each change is made under that lock, but the positions it broadcasts are worked out in a second,
 * separate call. A passing landing between the two is in both snapshots, so a request thread's broadcast can
 * carry a later picture than the timing thread's that follows it. Clients take the latest message as the
 * truth, and the stored result comes from the state itself, not from a broadcast, so this is harmless.
 */
@Service
public class LapTimingService {

    private static final Logger log = LoggerFactory.getLogger(LapTimingService.class);

    private final Map<Long, LiveRaceState> states = new ConcurrentHashMap<>();
    /**
     * Races whose state was released and that have not started again. A passing whose race lookup ran before the
     * finish or restart committed can reach {@link #onLapPassing} after the release; without this it would
     * recreate the state and the next start would carry one stale lap.
     */
    private final Set<Long> released = ConcurrentHashMap.newKeySet();
    private final LiveTimingHub liveTimingHub;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final LapThresholdQuery lapThresholdQuery;
    private final RaceClockService raceClockService;
    private final ApplicationEventPublisher publisher;
    /** Each running race's length and stagger, read once so a passing never queries for them. */
    private final Map<Long, RaceClockService.RacePlan> plans = new ConcurrentHashMap<>();

    @Autowired
    public LapTimingService(LiveTimingHub liveTimingHub,
                            RaceEntryRepository raceEntryRepository,
                            EntryRepository entryRepository,
                            CompetitorRepository competitorRepository,
                            LapThresholdQuery lapThresholdQuery,
                            RaceClockService raceClockService,
                            ApplicationEventPublisher publisher) {
        this.liveTimingHub = liveTimingHub;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.lapThresholdQuery = lapThresholdQuery;
        this.raceClockService = raceClockService;
        this.publisher = publisher;
    }

    /**
     * Returns existing LiveRaceState for this raceId, or creates a new one and pre-loads
     * entry display names so calculatePositions() can include driver names, and each entry's
     * minimum lap time. A change to the track's thresholds mid-race is not picked up.
     */
    public LiveRaceState stateFor(long raceId) {
        return states.computeIfAbsent(raceId, id -> {
            LiveRaceState state = new LiveRaceState();
            loadEntryNames(id, state);
            state.putMinLaps(lapThresholdQuery.minimumLapsForRace(id));
            state.putStartOffsets(startOffsets(id));
            return state;
        });
    }

    /** A competitor's public name changed (#265): races in progress take the new names from the next row. */
    @EventListener
    public void onNameVisibilityChanged(NameVisibilityChangedEvent event) {
        states.forEach(this::loadEntryNames);
    }

    /**
     * Read-only access — returns the state if present, empty if race has had no lap events yet.
     */
    public Optional<LiveRaceState> peek(long raceId) {
        return Optional.ofNullable(states.get(raceId));
    }

    /**
     * Handles a LapPassingEvent published by the ApplicationEventPublisher.
     * Resolves transponder number → entry ID, first checking runtime links (from retroactive
     * linking), then falling back to the entries' primary and secondary numbers in the DB.
     *
     * <p>A passing with no running race ({@link LapPassingEvent#NO_RACE}) is for practice only and is
     * ignored here; otherwise it would create state for a race that does not exist. A passing that resolves to an
     * entry also starts the race clock of a rolling start that is waiting for its first car
     * ({@link RaceClockService#onFirstPassing}).
     */
    @EventListener(LapPassingEvent.class)
    public void onLapPassing(LapPassingEvent event) {
        long raceId = event.raceId();
        if (raceId == LapPassingEvent.NO_RACE) {
            return;
        }
        if (released.contains(raceId)) {
            log.debug("Dropped a passing for race {}, which has been released", raceId);
            return;
        }
        String transponderNumber = event.transponderNumber();

        LiveRaceState state = stateFor(raceId);
        if (released.contains(raceId)) {
            // Released between the check above and stateFor, which then made a new state: take it away again
            states.remove(raceId);
            return;
        }

        // Check runtime links first — these take priority over transponderNumberSnapshot
        Long entryId = state.getRuntimeLink(transponderNumber);
        if (entryId == null) {
            entryId = resolveEntryId(raceId, transponderNumber);
        }

        if (entryId != null) {
            // A rolling start's clock waits for the first car; this starts it
            raceClockService.onFirstPassing(raceId);
        }

        boolean firstUnknown = state.applyLapPassing(event, entryId, System.currentTimeMillis(),
                windowEnded(raceId, state, entryId));

        if (entryId == null && firstUnknown) {
            liveTimingHub.broadcastUnknownTransponder(raceId, transponderNumber);
        }

        liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());

        if (allWindowsEnded(raceId, state) && state.takeFinishRequest()) {
            publisher.publishEvent(new AllCarsFlaggedEvent(raceId));
        }
    }

    /**
     * Whether the entry's time on the track has run out, so the next lap it counts is its last. A timed race gives
     * each car the race's length from its own start; in a stagger start the cars leave one after another, so the
     * last car's window ends after the first car's.
     */
    private boolean windowEnded(long raceId, LiveRaceState state, Long entryId) {
        if (entryId == null) {
            return false;
        }
        RaceClockService.RacePlan plan = planOf(raceId);
        if (plan.lapTarget() != null) {
            // A race of a set number of laps: the lap that makes the target is the car's last
            return state.lapsCompleted(entryId) + 1 >= plan.lapTarget();
        }
        if (plan.durationMs() == null) {
            return false;
        }
        long elapsedMs = raceClockService.clockOf(raceId, RaceStatus.RUNNING, plan.durationMs()).elapsedMs();
        return elapsedMs >= plan.durationMs() + state.startOffsetMs(entryId);
    }

    /** Whether the last car to start has had its whole window: until then a car yet to cross the line may be racing. */
    private boolean allWindowsEnded(long raceId, LiveRaceState state) {
        RaceClockService.RacePlan plan = planOf(raceId);
        if (plan.lapTarget() != null) {
            // No clock to wait for: the race is over when every car that crossed the line has taken the flag
            return true;
        }
        if (plan.durationMs() == null) {
            return false;
        }
        long elapsedMs = raceClockService.clockOf(raceId, RaceStatus.RUNNING, plan.durationMs()).elapsedMs();
        return elapsedMs >= plan.durationMs() + state.lastStartOffsetMs();
    }

    private RaceClockService.RacePlan planOf(long raceId) {
        return plans.computeIfAbsent(raceId, id -> raceClockService.planOf(id).orElse(RaceClockService.RacePlan.NONE));
    }

    /** When each of the race's entries starts, in grid order, for a stagger start; none for any other start. */
    private Map<Long, Long> startOffsets(long raceId) {
        long intervalMs = planOf(raceId).staggerIntervalMs();
        Map<Long, Long> offsets = new HashMap<>();
        if (intervalMs > 0) {
            // The order the drivers are called in: grid position, an entry with none last, then by entry
            List<RaceEntry> inGridOrder = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).stream()
                    .sorted(Comparator
                            .comparing(RaceEntry::getGridPosition, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(RaceEntry::getEntryId))
                    .toList();
            for (int i = 0; i < inGridOrder.size(); i++) {
                offsets.put(inGridOrder.get(i).getEntryId(), i * intervalMs);
            }
        }
        return offsets;
    }

    /**
     * Apply a marshal lap adjustment and rebroadcast positions.
     * Called by MarshalService after saving the MarshalAdjustment row.
     */
    public void applyMarshalAdjustment(long raceId, long entryId, int lapDelta, MarshalAdjustmentDto dto) {
        LiveRaceState state = stateFor(raceId);
        state.applyLapDelta(entryId, lapDelta);
        liveTimingHub.broadcastMarshalAdjustment(raceId, dto);
        liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());
    }

    /**
     * Takes a lap penalty off a car in live timing and rebroadcasts positions. Does nothing for a race with no live
     * timing; a penalty given after the finish is applied to the result instead.
     */
    public void applyLapPenalty(long raceId, long entryId, int laps) {
        peek(raceId).ifPresent(state -> {
            state.applyLapDelta(entryId, -laps);
            liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());
        });
    }

    /**
     * Releases in-memory state for a finished race, freeing memory, and drops any passing for the race until it
     * is running again.
     * Idempotent — no-op if no state is present.
     */
    public void releaseState(long raceId) {
        released.add(raceId);
        plans.remove(raceId);
        states.remove(raceId);
    }

    /**
     * A race going to RUNNING takes passings again. Done at once, not after the commit: the decoder lookup finds the
     * race as soon as the commit lands, which can be before an after-commit callback runs, and the passing would
     * then be dropped. If the start rolls back the race is not running, so no lookup finds it.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() == RaceStatus.GRID) {
            plans.remove(event.getRaceId());
        }
        if (event.getNewStatus() == RaceStatus.RUNNING) {
            released.remove(event.getRaceId());
        }
    }

    /**
     * Links an unknown transponder to an entry for the given race.
     * Retroactively credits all passings from that transponder since race start and
     * broadcasts updated positions via STOMP. Called from a request thread (the referee's
     * unknown-transponder flow) by {@code TransponderLinkService}; the link lives in memory only and
     * takes priority over the entries' own transponder numbers for the rest of the race.
     *
     * @return the positions after the credit, as broadcast
     */
    public List<dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto> linkTransponder(
            long raceId, String transponderNumber, long entryId) {
        LiveRaceState state = stateFor(raceId);
        List<dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto> positions =
                state.retroactiveLinkTransponder(transponderNumber, entryId);
        liveTimingHub.broadcastTimingUpdate(raceId, positions);
        return positions;
    }

    /**
     * Returns the count of lapHistory entries matching the given transponder number.
     * Used by TransponderLinkService to report lapsCredited before linking.
     */
    public int countPassingsForTransponder(long raceId, String transponderNumber) {
        LiveRaceState state = stateFor(raceId);
        return state.countPassingsForTransponder(transponderNumber);
    }

    /**
     * Pre-loads each entry's competitor name into the given state so calculatePositions()
     * can include driver names without hitting the DB on every lap. It is the public name (#265): live timing
     * goes to anonymous screens and the live feed.
     */
    private void loadEntryNames(long raceId, LiveRaceState state) {
        PublicNames.Snapshot names = PublicNames.snapshotFrom(competitorRepository);
        for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(raceId)) {
            state.putEntryCarNumber(raceEntry.getEntryId(),
                    raceEntry.getCarNumber() == null ? null : raceEntry.getCarNumber().toString());
            entryRepository.findById(raceEntry.getEntryId())
                    .map(Entry::getCompetitorId)
                    .flatMap(competitorRepository::findById)
                    .ifPresent(competitor -> state.putEntryName(raceEntry.getEntryId(),
                            names.of(competitor.getId(), competitor.getDisplayName(), raceEntry.getCarNumber())));
        }
    }

    /**
     * Resolves a transponder number to an entry ID for the given race. Either the entry's primary
     * or its secondary transponder matches (L6). Numbers are unique only within a race, so the
     * same number in another event never matters.
     *
     * <p>When the number belongs to more than one entry in this race it is ambiguous: no entry is
     * credited and the passing is treated as unknown, so a referee links it through the
     * unknown-transponder flow. Withdrawn entries never match.
     *
     * <p>Does NOT check runtime links — callers must check state.getRuntimeLink() first.
     *
     * <p>A database fault is not caught here: treating it as "unknown transponder" would send a referee to
     * link a passing that was never the transponder's fault. It reaches {@code DecoderListener}, which logs
     * the lost passing and carries on with the next one.
     */
    private Long resolveEntryId(long raceId, String transponderNumber) {
        // One query for the race's entries, one for their transponders: this runs on every passing
        List<Long> entryIds = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).stream()
                .map(RaceEntry::getEntryId)
                .toList();
        Set<Long> matches = new LinkedHashSet<>();
        for (Entry entry : entryRepository.findAllById(entryIds)) {
            if (entry.getStatus() != EntryStatus.WITHDRAWN
                    && (transponderNumber.equals(entry.getTransponderNumberSnapshot())
                        || transponderNumber.equals(entry.getSecondaryTransponderNumber()))) {
                matches.add(entry.getId());
            }
        }
        if (matches.size() == 1) {
            return matches.iterator().next();
        }
        if (matches.size() > 1) {
            log.warn("Transponder {} matches entries {} in race {} — not credited, flagged as unknown",
                    transponderNumber, matches, raceId);
        }
        return null;
    }
}
