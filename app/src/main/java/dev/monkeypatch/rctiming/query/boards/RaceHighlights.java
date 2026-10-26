package dev.monkeypatch.rctiming.query.boards;

import java.util.List;

/**
 * Works out the standouts of a set of runs for the overlay's ticker: the biggest gain and loss of places against the
 * grid, and the best average over three and over five laps in a row. Pure: it holds no state and touches no
 * database, so {@code RaceHighlightsService} can feed it a live heat or finished ones alike. Lap times are
 * milliseconds. Ties go to the run that comes first in the list, so callers pass cars in running order.
 */
public final class RaceHighlights {

    private RaceHighlights() {}

    /**
     * One car's run in one heat.
     *
     * @param driverName    the name to show, already as the public may see it
     * @param startPosition 1-based place on the grid, or null when the car had none (it then gains and loses nothing)
     * @param position      1-based place now, or at the flag
     * @param lapTimes      the car's lap durations in order, nulls for laps whose time is unknown
     */
    public record Run(String driverName, Integer startPosition, int position, List<Long> lapTimes) {}

    /** The standouts of {@code runs}; every mark is null for an empty list. */
    public static RaceHighlightsDto.Scope of(List<Run> runs) {
        RaceHighlightsDto.Mark gained = null;
        RaceHighlightsDto.Mark lost = null;
        RaceHighlightsDto.Mark avg3 = null;
        RaceHighlightsDto.Mark avg5 = null;
        for (Run run : runs) {
            if (run.startPosition() != null) {
                int change = run.startPosition() - run.position();
                if (change > 0 && (gained == null || change > gained.value())) {
                    gained = new RaceHighlightsDto.Mark(run.driverName(), change);
                }
                if (change < 0 && (lost == null || -change > lost.value())) {
                    lost = new RaceHighlightsDto.Mark(run.driverName(), -change);
                }
            }
            avg3 = quicker(avg3, run.driverName(), bestAverage(run.lapTimes(), 3));
            avg5 = quicker(avg5, run.driverName(), bestAverage(run.lapTimes(), 5));
        }
        return new RaceHighlightsDto.Scope(gained, lost, avg3, avg5);
    }

    private static RaceHighlightsDto.Mark quicker(RaceHighlightsDto.Mark current, String driverName, Long average) {
        return average != null && (current == null || average < current.value())
                ? new RaceHighlightsDto.Mark(driverName, average)
                : current;
    }

    /**
     * The best (lowest) average of {@code count} laps in a row, rounded to the millisecond, or null when there is no
     * run of that many laps with a known time (an unknown time breaks the run).
     */
    static Long bestAverage(List<Long> lapTimes, int count) {
        Long best = null;
        long sum = 0;
        int run = 0;
        for (int i = 0; i < lapTimes.size(); i++) {
            Long lap = lapTimes.get(i);
            if (lap == null || lap <= 0) {
                run = 0;
                sum = 0;
                continue;
            }
            sum += lap;
            run++;
            if (run > count) {
                sum -= lapTimes.get(i - count);
                run = count;
            }
            if (run == count) {
                long average = Math.round((double) sum / count);
                if (best == null || average < best) {
                    best = average;
                }
            }
        }
        return best;
    }
}
