package dev.monkeypatch.rctiming.livefeed;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a race's live running order into the standings both the live feed (#28) and the live results sent to
 * RaceHub carry, so the two always agree. Pure: no state, no threads.
 */
public final class LiveStandings {

    private LiveStandings() {
    }

    /**
     * The cars timed so far in running order, then the cars on the grid that have not crossed the line, in grid
     * order. A grid car with no name is left out. Names are the display names the public may see (#265).
     */
    public static List<LiveFeedV1.Standing> of(List<LiveTimingRowDto> rows, LiveFeedRaceLookup.RaceInfo race) {
        List<LiveFeedV1.Standing> standings = new ArrayList<>();
        Set<Long> timed = new HashSet<>();
        for (LiveTimingRowDto r : rows) {
            timed.add(r.entryId());
            standings.add(new LiveFeedV1.Standing(r.position(), r.driverName(), race.carNumber(r.entryId()),
                    r.lapsCompleted(), r.lastLapMs(), r.bestLapMs(), r.gapToLeaderMs(), r.gapToAheadMs(),
                    r.lapsDown()));
        }
        int leaderLaps = rows.isEmpty() ? 0 : rows.get(0).lapsCompleted();
        for (LiveFeedRaceLookup.GridCar car : race.grid()) {
            if (car.displayName() != null && !timed.contains(car.entryId())) {
                standings.add(new LiveFeedV1.Standing(standings.size() + 1, car.displayName(), car.carNumber(),
                        0, null, null, null, null, leaderLaps));
            }
        }
        return standings;
    }
}
