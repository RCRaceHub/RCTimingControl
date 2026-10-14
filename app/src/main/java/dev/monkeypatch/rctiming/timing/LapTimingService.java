package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory race state manager for live timing.
 * Holds a ConcurrentHashMap<Long, LiveRaceState> keyed by raceId.
 * Positions are calculated in memory and broadcast over STOMP — never persisted during a race.
 *
 * <p><strong>Threading.</strong> Decoder passings reach {@link #onLapPassing} synchronously on the
 * single timing thread ({@code timingExecutor}), so they are handled one at a time in decoder order and
 * broadcasts go out in that order. REST requests (marshal laps, lap penalties, transponder links) call in from
 * request threads, so every change to a {@link LiveRaceState} is made while holding that state's monitor.
 */
@Service
public class LapTimingService {

    private static final Logger log = LoggerFactory.getLogger(LapTimingService.class);

    private final Map<Long, LiveRaceState> states = new ConcurrentHashMap<>();
    private final LiveTimingHub liveTimingHub;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;

    public LapTimingService(LiveTimingHub liveTimingHub,
                            RaceEntryRepository raceEntryRepository,
                            EntryRepository entryRepository,
                            CompetitorRepository competitorRepository) {
        this.liveTimingHub = liveTimingHub;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
    }

    /**
     * Returns existing LiveRaceState for this raceId, or creates a new one and pre-loads
     * entry display names so calculatePositions() can include driver names.
     */
    public LiveRaceState stateFor(long raceId) {
        return states.computeIfAbsent(raceId, id -> {
            LiveRaceState state = new LiveRaceState();
            loadEntryNames(id, state);
            return state;
        });
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
     * ignored here; otherwise it would create state for a race that does not exist.
     */
    @EventListener(LapPassingEvent.class)
    public void onLapPassing(LapPassingEvent event) {
        long raceId = event.raceId();
        if (raceId == LapPassingEvent.NO_RACE) {
            return;
        }
        String transponderNumber = event.transponderNumber();

        LiveRaceState state = stateFor(raceId);

        // Check runtime links first — these take priority over transponderNumberSnapshot
        Long entryId = state.getRuntimeLink(transponderNumber);
        if (entryId == null) {
            entryId = resolveEntryId(raceId, transponderNumber);
        }

        boolean firstUnknown = state.applyLapPassing(event, entryId);

        if (entryId == null && firstUnknown) {
            liveTimingHub.broadcastUnknownTransponder(raceId, transponderNumber);
        }

        liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());
    }

    /**
     * Apply a marshal lap adjustment and rebroadcast positions.
     * Called by MarshalService after saving the MarshalAdjustment row.
     */
    public void applyMarshalAdjustment(long raceId, long entryId, int lapDelta, MarshalAdjustmentDto dto) {
        LiveRaceState state = stateFor(raceId);
        synchronized (state) {
            state.applyLapDelta(entryId, lapDelta);
        }
        liveTimingHub.broadcastMarshalAdjustment(raceId, dto);
        liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());
    }

    /**
     * Takes a lap penalty off a car in live timing and rebroadcasts positions. Does nothing for a race with no live
     * timing; a penalty given after the finish is applied to the result instead.
     */
    public void applyLapPenalty(long raceId, long entryId, int laps) {
        peek(raceId).ifPresent(state -> {
            synchronized (state) {
                state.applyLapDelta(entryId, -laps);
            }
            liveTimingHub.broadcastTimingUpdate(raceId, state.calculatePositions());
        });
    }

    /**
     * Releases in-memory state for a finished race, freeing memory.
     * Idempotent — no-op if no state is present.
     */
    public void releaseState(long raceId) {
        states.remove(raceId);
    }

    /**
     * Links an unknown transponder to an entry for the given race.
     * Retroactively credits all passings from that transponder since race start and
     * broadcasts updated positions via STOMP.
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
     * Pre-loads each entry's competitor display name into the given state so calculatePositions()
     * can include driver names without hitting the DB on every lap.
     */
    private void loadEntryNames(long raceId, LiveRaceState state) {
        for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(raceId)) {
            entryRepository.findById(raceEntry.getEntryId())
                    .map(Entry::getCompetitorId)
                    .flatMap(competitorRepository::findById)
                    .ifPresent(competitor ->
                            state.putEntryName(raceEntry.getEntryId(), competitor.getDisplayName()));
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
