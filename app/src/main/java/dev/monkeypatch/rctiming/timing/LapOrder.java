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
 * track ({@code BoardController}), so both draw the same lines. It is a plain value built from a snapshot of the
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

    private final List<LiveTimingRowDto> rows;
    private final Map<Long, LapClock> clocks;
    private final Map<Long, LapClock> orderClocks;

    private LapOrder(List<LiveTimingRowDto> rows, Map<Long, LapClock> clocks) {
        this.rows = rows;
        this.clocks = clocks;
        // Decided once for the whole race, so one car's place never comes from a different rule than another's
        // within a lap, or between laps.
        boolean orderUsable = rows.stream().allMatch(r -> r.lapsCompleted() == 0
                || (clocks.get(r.entryId()) != null && clocks.get(r.entryId()).lapTimes().size() == r.lapsCompleted()));
        this.orderClocks = orderUsable ? clocks : Map.of();
    }

    /**
     * Reads each car's lap times from the live state under its lock. {@code state} may be null (a legacy snapshot, or
     * no live state), in which case every place falls back to the final position. Where the crossing order can't be
     * worked out (a lap count changed by a marshal adjustment, for any car) the same fallback applies. A lapped
     * car's line ends where it stopped counting laps.
     *
     * @param rows  the final or current running order
     * @param state the race's live state, or null
     */
    public static LapOrder of(List<LiveTimingRowDto> rows, LiveRaceState state) {
        Map<Long, LapClock> clocks = new HashMap<>();
        if (state != null) {
            for (LiveTimingRowDto row : rows) {
                List<Long> lapTimes = state.lapTimesSnapshot(row.entryId());
                if (!lapTimes.isEmpty()) {
                    clocks.put(row.entryId(), new LapClock(row.lastPassingTimeMs(), lapTimes));
                }
            }
        }
        return new LapOrder(rows, clocks);
    }

    /** Laps completed by the leading car; zero before anyone has completed one. */
    public int leaderLaps() {
        return rows.stream().mapToInt(LiveTimingRowDto::lapsCompleted).max().orElse(0);
    }

    /** The place of each car that completed {@code lap}; see {@link #positionsAfterLap}. */
    public Map<Long, Integer> placesAfterLap(int lap) {
        return positionsAfterLap(lap, rows, orderClocks);
    }

    /** One car's place at the end of one lap. */
    public record Place(int lap, long entryId, int position) {}

    /** Every place held at the end of every lap, lap by lap, for a position chart. */
    public List<Place> places() {
        List<Place> places = new ArrayList<>();
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

    /** The duration of a car's {@code lap} (1-based), or null when it isn't known. */
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
