package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *
 * <p><strong>Units.</strong> Every time here is in milliseconds, from the passing's
 * {@code rtcTimeMicros / 1000}. {@code lastPassingTimeMs} is a wall-clock time, not a lap time, and
 * {@code 0} means the car has not passed yet: {@link #creditLap} uses that to tell a first passing, which
 * has no lap time, from a later one. So a car whose first passing landed exactly on the epoch would lose
 * that one lap time, which cannot happen with a decoder clock.
 */
public class LiveRaceState {

    final Map<Long, LiveRacePosition> positions = new HashMap<>();
    final List<LapPassingEvent> lapHistory = new ArrayList<>();
    final Set<String> seenUnknownTransponders = new HashSet<>();
    /** Runtime links: transponderNumber → entryId, populated by retroactiveLinkTransponder. */
    final Map<String, Long> runtimeLinks = new HashMap<>();
    /** Entry display names: entryId → "First Last", populated by LapTimingService on state creation. */
    final Map<Long, String> entryNames = new HashMap<>();
    /**
     * Fastest lap recorded across ALL entries this race session. It is never cleared: a lap removed by a
     * marshal adjustment or a penalty still counts, since those change lap counts, not lap times.
     */
    Long overallBestLapMs = null;
    /** Shortest lap that counts, by entry id (TRACK-02); an entry not listed has none. Set once, at state creation. */
    final Map<Long, Long> minLapMs = new HashMap<>();
    /** Server time, in epoch milliseconds, of the last passing that counted a lap; null before the first (TRACK-03). */
    Long lastLapCreditedAtMs = null;
    /** Entries that have taken the flag: their next passing after the clock ran out counted, and nothing after it does. */
    final Set<Long> flagged = new HashSet<>();
    /** Whether the finish has been asked for, so it is asked for once. */
    boolean finishRequested = false;

    /** Returns the runtime-linked entryId for this transponder, or null if not linked. */
    public synchronized Long getRuntimeLink(String transponderNumber) {
        return runtimeLinks.get(transponderNumber);
    }

    /** Sets the shortest lap that counts for each entry. Called by LapTimingService at state creation time. */
    public synchronized void putMinLaps(Map<Long, Long> minimums) {
        minLapMs.putAll(minimums);
    }

    /** Stores a display name for an entry. Called by LapTimingService at state creation time. */
    public void putEntryName(long entryId, String displayName) {
        entryNames.put(entryId, displayName);
    }

    /** As {@link #applyLapPassing(LapPassingEvent, Long, long)}, at the server's current time. */
    public boolean applyLapPassing(LapPassingEvent event, Long entryId) {
        return applyLapPassing(event, entryId, System.currentTimeMillis());
    }

    /**
     * Apply a lap passing event. Returns true if this is the first sighting of an unknown transponder.
     * If entryId is null, the transponder is unknown — tracked in seenUnknownTransponders.
     * All mutations are synchronized. {@code nowMs} is the server's clock when the passing arrived; it is kept when
     * the passing counts a lap, for the last lap timeout.
     */
    public synchronized boolean applyLapPassing(LapPassingEvent event, Long entryId, long nowMs) {
        return applyLapPassing(event, entryId, nowMs, false);
    }

    /**
     * As {@link #applyLapPassing(LapPassingEvent, Long, long)}, for a race whose clock may have run out. When
     * {@code clockExpired}, the passing that counts a lap is the car's last: it has taken the flag, and its later
     * passings are kept in the history but not counted. A passing too soon to count (TRACK-02) does not flag the car.
     */
    public synchronized boolean applyLapPassing(LapPassingEvent event, Long entryId, long nowMs,
                                                boolean clockExpired) {
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

        if (flagged.contains(entryId)) {
            return false;
        }
        if (creditLap(pos, passingTimeMs)) {
            lastLapCreditedAtMs = nowMs;
            if (clockExpired) {
                flagged.add(entryId);
            }
        }

        return false;
    }

    /**
     * Whether every car that has passed the line has taken the flag, so the race is over. False until at least one
     * car has passed. A car that never crossed is not waited for; the last lap timeout covers a car that stops.
     * Returns true once only, so the caller finishes the race once.
     */
    public synchronized boolean takeFinishRequest() {
        if (finishRequested || !allFlagged()) {
            return false;
        }
        finishRequested = true;
        return true;
    }

    private boolean allFlagged() {
        boolean any = false;
        for (LiveRacePosition pos : positions.values()) {
            if (pos.getLastPassingTimeMs() == 0) {
                continue;
            }
            any = true;
            if (!flagged.contains(pos.getEntryId())) {
                return false;
            }
        }
        return any;
    }

    /** Server time of the last passing that counted a lap, or empty if none has yet. */
    public synchronized Optional<Long> lastLapCreditedAtMs() {
        return Optional.ofNullable(lastLapCreditedAtMs);
    }

    /**
     * Apply a marshal lap delta (±1). Synchronized.
     *
     * <p>It moves {@code lapsCompleted}, the laps the car is credited with, and never
     * {@code LiveRacePosition.lapCount}, which counts the laps that were actually timed and divides the
     * average lap time. An entry with no passings yet gets a position so a +1 can be given before its first
     * lap; a −1 never takes it below zero.
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
     * <p>It assumes the target entry has no passings of its own: the replayed laps are credited on top of
     * whatever it already has, and lap times are worked out from the replay's own order, so linking a
     * transponder to an entry that is already being timed on another number double-counts.
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
     * passing) still counts the lap but gives no lap time, unless the entry has a minimum lap: then it is ignored.
     *
     * <p>A passing sooner after the car's last counted one than its minimum lap (TRACK-02) is ignored: a double
     * read of the loop or a cut. It leaves the car's last passing alone, so the next real crossing is timed from
     * the last lap that counted. A car's first passing has no lap to measure and is never ignored. Returns whether
     * the passing counted.
     */
    private boolean creditLap(LiveRacePosition pos, long passingTimeMs) {
        long prevPassingTime = pos.getLastPassingTimeMs();
        Long minimum = minLapMs.get(pos.getEntryId());
        if (minimum != null && prevPassingTime > 0 && passingTimeMs - prevPassingTime < minimum) {
            return false;
        }
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
        return true;
    }

    /**
     * Returns a single entry's live position for ResultSnapshotService. It is the live object, not a copy:
     * safe only because it is called once the race is FINISHED and no more passings change it.
     */
    public synchronized LiveRacePosition getPositionSnapshot(long entryId) {
        return positions.get(entryId);
    }
}
