package dev.monkeypatch.rctiming.query.boards;

import java.util.List;

/**
 * Works out the standouts of a set of runs for the overlay's ticker: the biggest gain and loss of places against the
 * grid, the best average over three and over five laps in a row, the flying lap, the most consistent driver and the
 * biggest improvement on the driver's previous heat. Pure: it holds no state and touches no
 * database, so {@code RaceHighlightsService} can feed it a live heat or finished ones alike. Lap times are
 * milliseconds. Ties go to the run that comes first in the list, so callers pass cars in running order.
 */
public final class RaceHighlights {

    /** Laps a car needs before its lap counts as flying: the average it beats is over the laps before it. */
    static final int FLYING_MIN_PRIOR_LAPS = 3;
    /** Timed laps, after the first, a car needs before it can be the most consistent; fewer is luck, not pace. */
    static final int CONSISTENT_MIN_LAPS = 5;

    private RaceHighlights() {}

    /**
     * One car's run in one heat.
     *
     * @param driverName    the name to show, already as the public may see it
     * @param startPosition 1-based place on the grid, or null when the car had none (it then gains and loses nothing)
     * @param position      1-based place now, or at the flag
     * @param lapTimes      the car's lap durations in order, nulls for laps whose time is unknown
     * @param previousLaps  laps the driver had done in their previous heat of the same round type by the time the
     *                      same race time had passed, or null when they have no previous heat
     */
    public record Run(String driverName, Integer startPosition, int position, List<Long> lapTimes, Integer previousLaps) {

        /** A run with no previous heat to compare with. */
        public Run(String driverName, Integer startPosition, int position, List<Long> lapTimes) {
            this(driverName, startPosition, position, lapTimes, null);
        }

        /** Laps completed, counting only laps with a known time. */
        int lapsDone() {
            return (int) lapTimes.stream().filter(lap -> lap != null && lap > 0).count();
        }
    }

    /** The standouts of {@code runs}; every mark is null for an empty list. */
    public static RaceHighlightsDto.Scope of(List<Run> runs) {
        RaceHighlightsDto.Mark gained = null;
        RaceHighlightsDto.Mark lost = null;
        RaceHighlightsDto.Mark avg3 = null;
        RaceHighlightsDto.Mark avg5 = null;
        RaceHighlightsDto.Mark flying = null;
        RaceHighlightsDto.Mark consistent = null;
        RaceHighlightsDto.Mark improved = null;
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
            flying = bigger(flying, run.driverName(), flyingMargin(run.lapTimes()));
            consistent = quicker(consistent, run.driverName(), spread(run.lapTimes()));
            if (run.previousLaps() != null) {
                improved = bigger(improved, run.driverName(), (long) run.lapsDone() - run.previousLaps());
            }
        }
        return new RaceHighlightsDto.Scope(gained, lost, avg3, avg5, flying, consistent, improved);
    }

    private static RaceHighlightsDto.Mark bigger(RaceHighlightsDto.Mark current, String driverName, Long value) {
        return value != null && value > 0 && (current == null || value > current.value())
                ? new RaceHighlightsDto.Mark(driverName, value)
                : current;
    }

    /**
     * How far, in milliseconds, the car's best lap beat the average of the timed laps before it, or null when no lap
     * has {@code FLYING_MIN_PRIOR_LAPS} timed laps before it or none was quicker than that average. The first lap
     * is left out, as in {@code spread}.
     */
    static Long flyingMargin(List<Long> lapTimes) {
        Long best = null;
        long sum = 0;
        int prior = 0;
        for (Long lap : timedAfterFirst(lapTimes)) {
            if (prior >= FLYING_MIN_PRIOR_LAPS) {
                long margin = Math.round((double) sum / prior) - lap;
                if (margin > 0 && (best == null || margin > best)) {
                    best = margin;
                }
            }
            sum += lap;
            prior++;
        }
        return best;
    }

    /**
     * Whether the car's latest timed lap is a flying lap: it beat the average of the timed laps before it (the first
     * lap left out) and had {@code FLYING_MIN_PRIOR_LAPS} of them to beat. Same rule as {@link #flyingMargin}, asked
     * of the last lap only, for the flying lap icon beside a driver on the live boards.
     */
    public static boolean lastLapFlying(List<Long> lapTimes) {
        List<Long> timed = timedAfterFirst(lapTimes);
        if (timed.size() <= FLYING_MIN_PRIOR_LAPS) {
            return false;
        }
        List<Long> prior = timed.subList(0, timed.size() - 1);
        long average = Math.round(prior.stream().mapToLong(Long::longValue).sum() / (double) prior.size());
        return average - timed.get(timed.size() - 1) > 0;
    }

    private static List<Long> timedAfterFirst(List<Long> lapTimes) {
        return lapTimes.stream().filter(lap -> lap != null && lap > 0).skip(1).toList();
    }

    /**
     * The standard deviation, in milliseconds, of the car's timed laps after its first (the start lap runs from a
     * standing grid and is not comparable), or null with fewer than {@code CONSISTENT_MIN_LAPS} such laps.
     */
    static Long spread(List<Long> lapTimes) {
        List<Long> laps = timedAfterFirst(lapTimes);
        if (laps.size() < CONSISTENT_MIN_LAPS) {
            return null;
        }
        double mean = laps.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = laps.stream().mapToDouble(lap -> (lap - mean) * (lap - mean)).sum() / laps.size();
        return Math.round(Math.sqrt(variance));
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
