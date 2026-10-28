package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who held which place at the end of each lap, worked out from the order the cars crossed the line. It serves the
 * stored lap history of a finished race ({@code ResultSnapshotService}) and the live position chart of a race on
 * track ({@code BoardController}), so both draw the same lines. It is a plain value built from copies of the
 * rows and lap times; it holds no lock and changes nothing. Times are milliseconds (UTC for passings).
 */
public final class LapOrder {

    /**
     * A car's lap times and the time of its last counted passing, which is all that is needed to work out when it
     * completed each lap.
     */
    public record LapClock(long lastPassingTimeMs, List<Long> lapTimes) {

        /** Time the car crossed the line to complete {@code lap} (1-based): its last passing minus the laps after it. */
        long completedLapAtMs(int lap) {
            long after = 0;
            for (int i = lap; i < lapTimes.size(); i++) {
                after += lapTimes.get(i);
            }
            return lastPassingTimeMs - after;
        }
    }

    /** One car's place at the end of one lap. */
    public record Place(int lap, long entryId, int position) {}

    private final List<LiveTimingRowDto> rows;
    private final Map<Long, LapClock> clocks;
    private final Map<Long, LapClock> orderClocks;
    private final boolean orderUsable;

    private LapOrder(List<LiveTimingRowDto> rows, Map<Long, LapClock> clocks) {
        this.rows = rows;
        this.clocks = clocks;
        // Decided once for the whole race, so one car's place never comes from a different rule than another's
        // within a lap, or between laps.
        this.orderUsable = rows.stream().allMatch(r -> r.lapsCompleted() == 0
                || (clocks.get(r.entryId()) != null && clocks.get(r.entryId()).lapTimes().size() == r.lapsCompleted()));
        this.orderClocks = this.orderUsable ? clocks : Map.of();
    }

    /**
     * Reads each car's lap times from the live state, one car at a time. That is not atomic with {@code rows}: for a
     * race still on track use {@link LiveRaceState#lapOrder()}, which copies both under the state's lock. For a
     * finished race, which no passing changes, this is safe. {@code state} may be null (a legacy snapshot, or no
     * live state), in which case every place falls back to the final position.
     *
     * @param rows  the final or current running order
     * @param state the race's live state, or null
     */
    public static LapOrder of(List<LiveTimingRowDto> rows, LiveRaceState state) {
        Map<Long, List<Long>> lapTimes = new HashMap<>();
        if (state != null) {
            for (LiveTimingRowDto row : rows) {
                lapTimes.put(row.entryId(), state.lapTimesSnapshot(row.entryId()));
            }
        }
        return of(rows, lapTimes);
    }

    /**
     * Builds the order from copied data, holding no lock. Where the crossing order can't be worked out (a lap count
     * changed by a marshal adjustment, for any car) every place falls back to the final position. A lapped car's
     * line ends where it stopped counting laps.
     *
     * @param rows     the running order
     * @param lapTimes each car's lap durations by entry id; a car missing or with none is treated as unknown
     */
    public static LapOrder of(List<LiveTimingRowDto> rows, Map<Long, List<Long>> lapTimes) {
        Map<Long, LapClock> clocks = new HashMap<>();
        for (LiveTimingRowDto row : rows) {
            List<Long> times = lapTimes.get(row.entryId());
            if (times != null && !times.isEmpty()) {
                clocks.put(row.entryId(), new LapClock(row.lastPassingTimeMs(), times));
            }
        }
        return new LapOrder(rows, clocks);
    }

    /**
     * Lap 0 for a position chart: each car's place on the grid. The grid is used rather than the order of first
     * crossing because that order is not where cars started (a rolling or staggered start, a quick car lower down
     * the grid), and it is the same lap 0 for every car. A car without a grid position is left out, and so is every car when the crossing
     * order can't be worked out (every later place is then just the final position, and a grid-to-final step on
     * lap 1 would be a move that never happened). The values are grid numbers, which may skip (a car that has
     * not crossed yet is absent), while later places are ranks.
     */
    public List<Place> startPlaces() {
        if (!orderUsable) return List.of();
        return rows.stream()
                .filter(r -> r.startPosition() != null)
                .sorted(Comparator.comparingInt(LiveTimingRowDto::startPosition))
                .map(r -> new Place(0, r.entryId(), r.startPosition()))
                .toList();
    }

    /** Laps completed by the leading car; zero before anyone has completed one. */
    public int leaderLaps() {
        return rows.stream().mapToInt(LiveTimingRowDto::lapsCompleted).max().orElse(0);
    }

    /** The place of each car that completed {@code lap}; see {@link #positionsAfterLap}. */
    public Map<Long, Integer> placesAfterLap(int lap) {
        return positionsAfterLap(lap, rows, orderClocks);
    }

    /**
     * Every place held at the end of every lap, lap by lap, for a position chart. It opens with lap 0, the grid
     * order the cars set off from, so the chart shows who gained or lost places on the first lap; a car with no
     * grid position has no lap 0 point and its line starts at lap 1.
     */
    public List<Place> places() {
        List<Place> places = new ArrayList<>(startPlaces());
        for (int lap = 1; lap <= leaderLaps(); lap++) {
            Map<Long, Integer> atLap = placesAfterLap(lap);
            for (LiveTimingRowDto row : rows) {
                if (row.lapsCompleted() >= lap) {
                    places.add(new Place(lap, row.entryId(), atLap.getOrDefault(row.entryId(), row.position())));
                }
            }
        }
        return places;
    }

    /**
     * The duration of a car's {@code lap} (1-based), or null when it isn't known. Unlike the places this doesn't need
     * the whole race's lap counts to agree, so it is given whenever the car has the time.
     */
    public Long lapTimeMs(long entryId, int lap) {
        LapClock clock = clocks.get(entryId);
        return clock != null && lap <= clock.lapTimes().size() ? clock.lapTimes().get(lap - 1) : null;
    }

    /**
     * The place of each car that completed {@code lap}, by the order they crossed the line to complete it: 1 is first
     * across. Cars that have not completed the lap are not placed. If any car that completed the lap has no usable
     * lap times (none recorded, or a count that differs from its laps because a marshal adjustment changed it)
     * the crossing order can't be trusted, so every car falls back to its final position.
     */
    static Map<Long, Integer> positionsAfterLap(int lap, List<LiveTimingRowDto> rows, Map<Long, LapClock> clocks) {
        List<LiveTimingRowDto> completed = rows.stream().filter(r -> r.lapsCompleted() >= lap).toList();
        boolean usable = completed.stream().allMatch(r -> {
            LapClock clock = clocks.get(r.entryId());
            return clock != null && clock.lapTimes().size() == r.lapsCompleted();
        });
        Map<Long, Integer> places = new HashMap<>();
        if (!usable) {
            completed.forEach(r -> places.put(r.entryId(), r.position()));
            return places;
        }
        List<LiveTimingRowDto> crossing = new ArrayList<>(completed);
        crossing.sort(Comparator
                .comparingLong((LiveTimingRowDto r) -> clocks.get(r.entryId()).completedLapAtMs(lap))
                .thenComparingInt(LiveTimingRowDto::position));
        for (int i = 0; i < crossing.size(); i++) {
            places.put(crossing.get(i).entryId(), i + 1);
        }
        return places;
    }
}
