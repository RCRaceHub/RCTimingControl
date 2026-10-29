package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import dev.monkeypatch.rctiming.timing.battle.BattleGroupDto;
import dev.monkeypatch.rctiming.timing.battle.BattleTracker;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import dev.monkeypatch.rctiming.query.boards.RaceHighlights;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    /** Entry car numbers for this race: entryId → car number; an entry with none recorded has no key. */
    final Map<Long, String> entryCarNumbers = new HashMap<>();
    /** Each entry's 1-based grid place for this race, which the boards compare with its place in the order. */
    final Map<Long, Integer> entryStartPositions = new HashMap<>();
    /**
     * Fastest lap recorded across ALL entries this race session. It is never cleared: a lap removed by a
     * marshal adjustment or a penalty still counts, since those change lap counts, not lap times.
     */
    Long overallBestLapMs = null;
    /** Spots the race's on-track battles from each update of the order (#505). */
    private final BattleTracker battleTracker;
    /** The battles as of the last update, for a client that connects between updates. */
    private List<BattleGroupDto> battles = List.of();
    /** Shortest lap that counts, by entry id (TRACK-02); an entry not listed has none. Set once, at state creation. */
    final Map<Long, Long> minLapMs = new HashMap<>();
    /** Server time, in epoch milliseconds, of the last passing that counted a lap; null before the first (TRACK-03). */
    Long lastLapCreditedAtMs = null;
    /** Decoder time, in milliseconds, of the first passing from an entry in the race; 0 until one crosses. */
    private long firstCrossingMs;
    /** When the race started, in epoch milliseconds; 0 until known. A rolling start counts from the first crossing. */
    private long startedAtMs;
    /** Whether the race starts rolling, so total times run from {@link #firstCrossingMs} rather than the start. */
    private boolean rollingStart;
    /** Server time, in epoch milliseconds, when the first car took the flag; null until one has. */
    Long firstFlaggedAtMs = null;
    /** Entries that have taken the flag: the lap that counted after the clock ran out was their last. */
    final Set<Long> flagged = new HashSet<>();
    /**
     * When each entry's own time on the track starts, in milliseconds after the race clock starts: a stagger start
     * sends the cars off one at a time, so each has its own window. An entry not listed starts at 0.
     */
    final Map<Long, Long> startOffsetMs = new HashMap<>();
    /** Whether the finish has been asked for, so it is asked for once. */
    boolean finishRequested = false;
    /** Whether the race was ended by its maximum race window, so cars not yet flagged did not finish. */
    private boolean cutOff = false;
    /** Entries that crossed the line before their turn in a stagger start: entryId → how early the first crossing was. */
    final Map<Long, Long> jumpStartEarlyMs = new LinkedHashMap<>();
    /** Jump starts the referee has taken no action on. */
    final Set<Long> jumpStartDismissed = new HashSet<>();
    /** Seconds the referee added to an entry's time, in milliseconds, summed; an entry with none has no key. */
    final Map<Long, Long> timePenaltyMs = new HashMap<>();

    public LiveRaceState() {
        this(BattleTracker.Settings.defaults());
    }

    /** @param battleSettings the club's battle thresholds (#505) */
    public LiveRaceState(BattleTracker.Settings battleSettings) {
        this.battleTracker = new BattleTracker(battleSettings);
    }

    /** Applies changed battle thresholds from the next update. */
    public void setBattleSettings(BattleTracker.Settings battleSettings) {
        battleTracker.setSettings(battleSettings);
    }

    /**
     * Sets when the race started, for each row's total time. Called at state creation time.
     *
     * @param startedAtMs epoch milliseconds the race started, 0 when unknown
     * @param rolling     whether the start is rolling, which counts from the first car's crossing instead
     */
    public synchronized void putRaceStart(long startedAtMs, boolean rolling) {
        this.startedAtMs = startedAtMs;
        this.rollingStart = rolling;
    }

    /** Whether {@link #putRaceStart} has been given a start, so a state made before the race started can be topped up. */
    public synchronized boolean hasRaceStart() {
        return startedAtMs > 0;
    }

    /** Returns the runtime-linked entryId for this transponder, or null if not linked. */
    public synchronized Long getRuntimeLink(String transponderNumber) {
        return runtimeLinks.get(transponderNumber);
    }

    /** Sets when each entry starts, in milliseconds after the race clock starts. Called at state creation time. */
    public synchronized void putStartOffsets(Map<Long, Long> offsets) {
        startOffsetMs.putAll(offsets);
    }

    /**
     * When the entry's own time on the track starts, in milliseconds after the race clock starts. An entry added to
     * the race after the offsets were set is taken as the last to start, so it is never flagged before its time.
     */
    public synchronized long startOffsetMs(long entryId) {
        return startOffsetMs.getOrDefault(entryId, lastStartOffsetMs());
    }

    /** Whether the entry has a slot in the stagger; false for an entry added after the offsets were set. */
    public synchronized boolean hasStartOffset(long entryId) {
        return startOffsetMs.containsKey(entryId);
    }

    /** When the last car starts, in milliseconds after the race clock starts; 0 when they all start together. */
    public synchronized long lastStartOffsetMs() {
        return startOffsetMs.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    }

    /**
     * Notes that an entry crossed the line {@code earlyMs} before its start. Only the entry's first early crossing is
     * kept, so the referee is told once. Returns true if this is that first crossing.
     */
    public synchronized boolean recordJumpStart(long entryId, long earlyMs) {
        return jumpStartEarlyMs.putIfAbsent(entryId, earlyMs) == null;
    }

    /** The entry's jump start if it has one the referee has not yet dealt with; empty if none, or already dismissed. */
    public synchronized Optional<JumpStartDto> openJumpStart(long entryId) {
        if (jumpStartDismissed.contains(entryId)) {
            return Optional.empty();
        }
        return jumpStarts().stream().filter(j -> j.entryId() == entryId).findFirst();
    }

    /**
     * Notes that the referee dealt with an entry's jump start, so the alert goes but the entry stays listed.
     *
     * @return true if the entry had an open jump start; false if it has none, or it was already dismissed
     */
    public synchronized boolean dismissJumpStart(long entryId) {
        return jumpStartEarlyMs.containsKey(entryId) && jumpStartDismissed.add(entryId);
    }

    /** The entries that have jumped the start so far, in the order they did, with names and car numbers. */
    public synchronized List<JumpStartDto> jumpStarts() {
        List<JumpStartDto> result = new ArrayList<>(jumpStartEarlyMs.size());
        jumpStartEarlyMs.forEach((entryId, earlyMs) -> result.add(new JumpStartDto(entryId,
                entryNames.getOrDefault(entryId, "Entry " + entryId), entryCarNumbers.get(entryId), earlyMs,
                jumpStartDismissed.contains(entryId))));
        return result;
    }

    /** Sets the shortest lap that counts for each entry. Called by LapTimingService at state creation time. */
    public synchronized void putMinLaps(Map<Long, Long> minimums) {
        minLapMs.putAll(minimums);
    }

    /** Stores a display name for an entry. Called by LapTimingService at state creation time. */
    public void putEntryName(long entryId, String displayName) {
        entryNames.put(entryId, displayName);
    }

    /** Stores an entry's car number for this race, shown beside its position. A null number is ignored. */
    public void putEntryCarNumber(long entryId, String carNumber) {
        if (carNumber != null) {
            entryCarNumbers.put(entryId, carNumber);
        }
    }

    /** Stores an entry's 1-based grid position for this race, which the boards compare with its place. Null is ignored. */
    public void putEntryStartPosition(long entryId, Integer startPosition) {
        if (startPosition != null) {
            entryStartPositions.put(entryId, startPosition);
        }
    }

    /** Replaces every entry's grid place, for when the grid was edited after this state was built. */
    public void replaceStartPositions(Map<Long, Integer> startPositions) {
        entryStartPositions.clear();
        startPositions.forEach(this::putEntryStartPosition);
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
     * As {@link #applyLapPassing(LapPassingEvent, Long, long)}, for a race with a length, where the car's time on
     * the track may have run out. When {@code windowEnded}, the passing that counts a lap is the car's last: it has
     * taken the flag, and its later passings are kept in the history but not counted. A passing too soon to count
     * (TRACK-02) does not flag the car, and neither does a first passing, which only starts timing the first lap.
     */
    public synchronized boolean applyLapPassing(LapPassingEvent event, Long entryId, long nowMs,
                                                boolean windowEnded) {
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

        if (firstCrossingMs == 0) {
            firstCrossingMs = passingTimeMs;
        }
        if (flagged.contains(entryId)) {
            return false;
        }
        if (creditLap(pos, passingTimeMs)) {
            lastLapCreditedAtMs = nowMs;
            if (windowEnded && pos.getLapsCompleted() > 0) {
                flagged.add(entryId);
                if (firstFlaggedAtMs == null) {
                    firstFlaggedAtMs = nowMs;
                }
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

    /** Records that the maximum race window ended the race, before the finish is taken (#428). */
    public synchronized void markCutOff() {
        cutOff = true;
    }

    /** Withdraws {@link #markCutOff()} when the finish it was for did not happen. */
    public synchronized void clearCutOff() {
        cutOff = false;
    }

    /** Whether the maximum race window ended the race: a car with laps that never took the flag did not finish. */
    public synchronized boolean isCutOff() {
        return cutOff;
    }

    /** Server time, in epoch milliseconds, when the first car took the flag, if one has. */
    public synchronized Optional<Long> firstFlaggedAtMs() {
        return Optional.ofNullable(firstFlaggedAtMs);
    }

    /** The laps the car is credited with, 0 if it has not crossed the line. */
    public synchronized int lapsCompleted(long entryId) {
        LiveRacePosition pos = positions.get(entryId);
        return pos == null ? 0 : pos.getLapsCompleted();
    }

    /** Whether every car that has passed the line has taken the flag; the same test as {@link #takeFinishRequest()}. */
    public synchronized boolean allFlagged() {
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

    /**
     * Decoder time (UTC milliseconds) of the first passing from an entry in the race, which is when a rolling start's
     * clock starts; 0 when no car has crossed.
     */
    public synchronized long firstCrossingMs() {
        return firstCrossingMs;
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
     * Adds a referee's time penalty to an entry's time, so the live order and gaps match the result the race will be
     * given. Penalties add up. Synchronized.
     *
     * @param penaltyMs milliseconds to add to the entry's total time; zero or less is ignored
     */
    public synchronized void applyTimePenalty(long entryId, long penaltyMs) {
        if (penaltyMs > 0) {
            timePenaltyMs.merge(entryId, penaltyMs, Long::sum);
        }
    }

    /** The entry's last passing time with its time penalties added, which is what it is ranked and gapped by. */
    private long rankedPassingMs(LiveRacePosition pos, boolean withPenalties) {
        long last = pos.getLastPassingTimeMs();
        // A car that has not crossed has no time to add to
        return withPenalties && last > 0 ? last + timePenaltyMs.getOrDefault(pos.getEntryId(), 0L) : last;
    }

    /**
     * Calculate current positions and return a sorted snapshot, with time penalties added to each car's time.
     * Sorted by: lapsCompleted DESC, penalised last passing time ASC (earlier finish = better position on same lap).
     * Gap to leader = |leaderLastPassingMs - myLastPassingMs| (same-logical-clock approximation), also penalised.
     * Time gaps are only meaningful for cars on the same lap. Cars on fewer laps carry
     * lapsDown > 0; the frontend renders "+N Lap(s)" instead of a time for those rows.
     */
    public synchronized List<LiveTimingRowDto> calculatePositions() {
        return calculatePositions(true);
    }

    /**
     * The positions as timed, with no time penalties: the base the stored result's corrections are applied to
     * ({@code ResultSnapshotService}), so a penalty is added once.
     */
    public synchronized List<LiveTimingRowDto> calculateTimedPositions() {
        return calculatePositions(false);
    }

    private List<LiveTimingRowDto> calculatePositions(boolean withPenalties) {
        List<LiveRacePosition> sorted = positions.values().stream()
                .sorted(Comparator
                        .comparingInt(LiveRacePosition::getLapsCompleted).reversed()
                        .thenComparingLong(pos -> rankedPassingMs(pos, withPenalties)))
                .toList();

        List<LiveTimingRowDto> result = new ArrayList<>(sorted.size());
        int leaderLaps = sorted.isEmpty() ? 0 : sorted.get(0).getLapsCompleted();
        Long leaderLastPassing = sorted.isEmpty() ? null : rankedPassingMs(sorted.get(0), withPenalties);
        Long prevLastPassing = null;
        int prevLaps = leaderLaps;

        for (int i = 0; i < sorted.size(); i++) {
            LiveRacePosition pos = sorted.get(i);
            int position = i + 1;
            int lapsDown = leaderLaps - pos.getLapsCompleted();
            int intervalLapsDown = prevLaps - pos.getLapsCompleted();

            // Time gaps only valid between cars on the same lap; null them when laps differ
            Long gapToLeader = (i == 0 || leaderLastPassing == null || lapsDown > 0) ? null
                    : Math.abs(leaderLastPassing - rankedPassingMs(pos, withPenalties));
            Long gapToAhead = (i == 0 || prevLastPassing == null || intervalLapsDown > 0) ? null
                    : Math.abs(prevLastPassing - rankedPassingMs(pos, withPenalties));

            result.add(new LiveTimingRowDto(
                    pos.getEntryId(),
                    entryNames.getOrDefault(pos.getEntryId(), "Entry " + pos.getEntryId()),
                    entryCarNumbers.get(pos.getEntryId()),
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
                    gapToAhead,
                    totalTimeMs(pos, withPenalties),
                    flagged.contains(pos.getEntryId()),
                    withPenalties ? timePenaltyMs.getOrDefault(pos.getEntryId(), 0L) : 0L,
                    entryStartPositions.get(pos.getEntryId()),
                    RaceHighlights.lastLapFlying(pos.getLapTimes())
            ));
            prevLastPassing = rankedPassingMs(pos, withPenalties);
            prevLaps = pos.getLapsCompleted();
        }
        return result;
    }

    /**
     * Start to the car's last crossing plus its time penalties, as the result keeps it; null before it has crossed
     * or with no known start.
     */
    private Long totalTimeMs(LiveRacePosition pos, boolean withPenalties) {
        long start = rollingStart ? firstCrossingMs : startedAtMs;
        return start > 0 && pos.getLastPassingTimeMs() > start
                ? rankedPassingMs(pos, withPenalties) - start : null;
    }

    /**
     * Takes an update of the order into the battle tracker and returns the battles it shows (#505). Called by
     * {@code LapTimingService} for each update it broadcasts, on the timing thread or a request thread.
     *
     * @param rows the order as just broadcast
     */
    public synchronized List<BattleGroupDto> advanceBattles(List<LiveTimingRowDto> rows) {
        battles = battleTracker.advance(rows);
        return battles;
    }

    /** The order and the battles it shows, taken together, and whether the battles differ from the last update. */
    public record TimingSnapshot(List<LiveTimingRowDto> rows, List<BattleGroupDto> battles, boolean battlesChanged) {
    }

    /**
     * Works out the order and takes it into the battle tracker under one lock, so two threads (the timing thread and a
     * request thread giving a penalty) never feed the tracker older rows after newer ones (#505).
     */
    public synchronized TimingSnapshot snapshot() {
        List<LiveTimingRowDto> rows = calculatePositions();
        List<BattleGroupDto> before = battles;
        List<BattleGroupDto> after = advanceBattles(rows);
        return new TimingSnapshot(rows, after, !after.equals(before));
    }

    /** The battles as of the last update, empty before the first. */
    public synchronized List<BattleGroupDto> battles() {
        return battles;
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
     * Records a passing for a position. A car's first passing after the start is not a lap: it only
     * starts the timing of the first lap. Every later passing counts one more lap and works out its lap
     * time from the previous passing: last, best and average lap, and the race's overall best. A gap of 0
     * or less (a repeat or out-of-order passing) still counts the lap but gives no lap time, unless the
     * entry has a minimum lap: then it is ignored.
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
        pos.setLastPassingTimeMs(passingTimeMs);
        if (prevPassingTime > 0) {
            pos.setLapsCompleted(pos.getLapsCompleted() + 1);
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

    /**
     * A copy of one entry's lap durations (milliseconds, in the order the laps were counted), taken under the lock so
     * it is consistent with a concurrent passing. Empty when the entry has none.
     */
    public synchronized List<Long> lapTimesSnapshot(long entryId) {
        LiveRacePosition pos = positions.get(entryId);
        return pos == null || pos.getLapTimes() == null ? List.of() : List.copyOf(pos.getLapTimes());
    }

    /**
     * The running order and the place each car held at the end of every lap so far. The rows and every car's lap
     * times are copied together under the lock, so a passing arriving meanwhile can't leave a car's lap times and lap
     * count out of step; the ordering itself is worked out after the lock is released, so the timing thread isn't
     * held up. Called per request from the boards' position chart, on a Tomcat thread.
     */
    public LapOrder lapOrder() {
        List<LiveTimingRowDto> rows;
        Map<Long, List<Long>> lapTimes = new HashMap<>();
        synchronized (this) {
            rows = calculatePositions();
            for (LiveTimingRowDto row : rows) {
                lapTimes.put(row.entryId(), lapTimesSnapshot(row.entryId()));
            }
        }
        return LapOrder.of(rows, lapTimes);
    }
}
