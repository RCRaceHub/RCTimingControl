package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-race in-memory position model. Live positions are never written to the database during a race.
 * NOT a Spring bean — one instance per raceId, held in LapTimingService.states ConcurrentHashMap.
 *
 * <p>All mutating methods are synchronized on {@code this}: the timing thread changes the state while
 * request threads read it and link transponders.
 * Position recalculation is O(n log n) over typically ≤40 entries — negligible latency.
 *
 * <p>Gaps are the difference between last passing times, and are given only between cars on the same lap.
 */
public class LiveRaceState {

    final Map<Long, LiveRacePosition> positions = new HashMap<>();
    final List<LapPassingEvent> lapHistory = new ArrayList<>();
    final Set<String> seenUnknownTransponders = new HashSet<>();
    /** Runtime links: transponderNumber → entryId, populated by retroactiveLinkTransponder. */
    final Map<String, Long> runtimeLinks = new HashMap<>();
    /** Entry display names: entryId → "First Last", populated by LapTimingService on state creation. */
    final Map<Long, String> entryNames = new HashMap<>();
    /** Fastest lap recorded across ALL entries this race session. */
    Long overallBestLapMs = null;

    /** Returns the runtime-linked entryId for this transponder, or null if not linked. */
    public synchronized Long getRuntimeLink(String transponderNumber) {
        return runtimeLinks.get(transponderNumber);
    }

    /** Stores a display name for an entry. Called by LapTimingService at state creation time. */
    public void putEntryName(long entryId, String displayName) {
        entryNames.put(entryId, displayName);
    }

    /**
     * Apply a lap passing event. Returns true if this is the first sighting of an unknown transponder.
     * If entryId is null, the transponder is unknown — tracked in seenUnknownTransponders.
     * All mutations are synchronized.
     */
    public synchronized boolean applyLapPassing(LapPassingEvent event, Long entryId) {
        lapHistory.add(event);
        long passingTimeMs = event.rtcTimeMicros() / 1000L;

        if (entryId == null) {
            // Unknown transponder — track first sighting
            return seenUnknownTransponders.add(event.transponderNumber());
        }

        LiveRacePosition pos = positions.computeIfAbsent(entryId, id -> {
            LiveRacePosition p = new LiveRacePosition();
            p.setEntryId(id);
            return p;
        });

        creditLap(pos, passingTimeMs);

        return false;
    }

    /**
     * Apply a marshal lap delta (±1). Synchronized.
     */
    public synchronized void applyLapDelta(long entryId, int lapDelta) {
        LiveRacePosition pos = positions.computeIfAbsent(entryId, id -> {
            LiveRacePosition p = new LiveRacePosition();
            p.setEntryId(id);
            return p;
        });
        pos.setLapsCompleted(Math.max(0, pos.getLapsCompleted() + lapDelta));
    }

    /**
     * Calculate current positions and return a sorted snapshot.
     * Sorted by: lapsCompleted DESC, lastPassingTimeMs ASC (earlier finish = better position on same lap).
     * Gap to leader = |leaderLastPassingMs - myLastPassingMs| (same-logical-clock approximation).
     * Time gaps are only meaningful for cars on the same lap. Cars on fewer laps carry
     * lapsDown > 0; the frontend renders "+N Lap(s)" instead of a time for those rows.
     */
    public synchronized List<LiveTimingRowDto> calculatePositions() {
        List<LiveRacePosition> sorted = positions.values().stream()
                .sorted(Comparator
                        .comparingInt(LiveRacePosition::getLapsCompleted).reversed()
                        .thenComparingLong(LiveRacePosition::getLastPassingTimeMs))
                .toList();

        List<LiveTimingRowDto> result = new ArrayList<>(sorted.size());
        int leaderLaps = sorted.isEmpty() ? 0 : sorted.get(0).getLapsCompleted();
        Long leaderLastPassing = sorted.isEmpty() ? null : sorted.get(0).getLastPassingTimeMs();
        Long prevLastPassing = null;
        int prevLaps = leaderLaps;

        for (int i = 0; i < sorted.size(); i++) {
            LiveRacePosition pos = sorted.get(i);
            int position = i + 1;
            int lapsDown = leaderLaps - pos.getLapsCompleted();
            int intervalLapsDown = prevLaps - pos.getLapsCompleted();

            // Time gaps only valid between cars on the same lap; null them when laps differ
            Long gapToLeader = (i == 0 || leaderLastPassing == null || lapsDown > 0) ? null
                    : Math.abs(leaderLastPassing - pos.getLastPassingTimeMs());
            Long gapToAhead = (i == 0 || prevLastPassing == null || intervalLapsDown > 0) ? null
                    : Math.abs(prevLastPassing - pos.getLastPassingTimeMs());

            result.add(new LiveTimingRowDto(
                    pos.getEntryId(),
                    entryNames.getOrDefault(pos.getEntryId(), "Entry " + pos.getEntryId()),
                    position,
                    pos.getLapsCompleted(),
                    pos.getLastPassingTimeMs(),
                    pos.getLastLapMs(),
                    pos.getBestLapMs(),
                    pos.getAvgLapMs(),
                    overallBestLapMs,
                    lapsDown,
                    intervalLapsDown,
                    gapToLeader,
                    gapToAhead
            ));
            prevLastPassing = pos.getLastPassingTimeMs();
            prevLaps = pos.getLapsCompleted();
        }
        return result;
    }

    public List<LapPassingEvent> getLapHistory() {
        return lapHistory;
    }

    /**
     * Retroactively credit all lapHistory entries for an unknown transponder
     * to the now-identified entry. Removes the transponder from the unknown set and recalculates
     * positions. Idempotent — if the transponder is already linked to the same entry, returns
     * current positions without replaying history (prevents lap doubling on re-submit).
     *
     * <p>Uses {@link #applyPositionUpdate} (not {@link #applyLapPassing}) to avoid
     * re-adding events to lapHistory during iteration, which would cause
     * ConcurrentModificationException and duplicate history entries.
     *
     * @return recalculated position list; empty list if no matching lapHistory entries.
     */
    public synchronized List<LiveTimingRowDto> retroactiveLinkTransponder(
            String transponderNumber, long entryId) {
        // Idempotency guard: if already linked to this entry, skip replay to prevent double-counting
        Long existingLink = runtimeLinks.get(transponderNumber);
        if (existingLink != null && existingLink.equals(entryId)) {
            return calculatePositions();
        }
        for (LapPassingEvent event : lapHistory) {
            if (transponderNumber.equals(event.transponderNumber())) {
                applyPositionUpdate(event, entryId);
            }
        }
        runtimeLinks.put(transponderNumber, entryId);
        seenUnknownTransponders.remove(transponderNumber);
        return calculatePositions();
    }

    /**
     * Counts lapHistory entries for a given transponder number.
     * Used by TransponderLinkService to report lapsCredited before linking.
     */
    public synchronized int countPassingsForTransponder(String transponderNumber) {
        return (int) lapHistory.stream()
                .filter(e -> transponderNumber.equals(e.transponderNumber()))
                .count();
    }

    /**
     * Updates the in-memory position state for a passing event WITHOUT adding to lapHistory.
     * Used by retroactiveLinkTransponder to replay historical events for a newly-linked entry.
     */
    private void applyPositionUpdate(LapPassingEvent event, Long entryId) {
        long passingTimeMs = event.rtcTimeMicros() / 1000L;
        if (entryId == null) {
            return;
        }
        LiveRacePosition pos = positions.computeIfAbsent(entryId, id -> {
            LiveRacePosition p = new LiveRacePosition();
            p.setEntryId(id);
            return p;
        });
        creditLap(pos, passingTimeMs);
    }

    /**
     * Counts one more lap for a position and works out its lap time from the previous passing: last,
     * best and average lap, and the race's overall best. A gap of 0 or less (a repeat or out-of-order
     * passing) still counts the lap but gives no lap time.
     */
    private void creditLap(LiveRacePosition pos, long passingTimeMs) {
        long prevPassingTime = pos.getLastPassingTimeMs();
        pos.setLapsCompleted(pos.getLapsCompleted() + 1);
        pos.setLastPassingTimeMs(passingTimeMs);
        if (prevPassingTime > 0) {
            long lapMs = passingTimeMs - prevPassingTime;
            if (lapMs > 0) {
                pos.setLastLapMs(lapMs);
                pos.accumulateLap(lapMs);
                pos.getLapTimes().add(lapMs);
                Long currentBest = pos.getBestLapMs();
                if (currentBest == null || lapMs < currentBest) {
                    pos.setBestLapMs(lapMs);
                }
                if (overallBestLapMs == null || lapMs < overallBestLapMs) {
                    overallBestLapMs = lapMs;
                }
            }
        }
    }

    /**
     * Returns a single entry's live position for ResultSnapshotService. It is the live object, not a copy:
     * safe only because it is called once the race is FINISHED and no more passings change it.
     */
    public synchronized LiveRacePosition getPositionSnapshot(long entryId) {
        return positions.get(entryId);
    }
}
