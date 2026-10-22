package dev.monkeypatch.rctiming.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores a final run in legs, as the BRCA A final is (#427): in each leg a driver scores their finishing place
 * (1 for the winner), the best {@code legsCounted} legs are added and the lowest total wins. A tie goes to the best
 * single counting leg, then the second best, then the most laps over the counting legs, then the least time over
 * them. A driver who did not run a leg (no laps) scores one more than the number of drivers.
 *
 * <p>Pure: no Spring, no database. {@link FinalLegsService} and the championship standings read the legs' results
 * and call it.
 */
public final class FinalLegScoring {

    /** A leg's time for a driver is capped at this, so adding legs cannot overflow. */
    private static final long MAX_LEG_TIME_MS = 24L * 60 * 60 * 1000;

    private FinalLegScoring() {
    }

    /** One driver's run in one leg. */
    public record Run(int laps, long totalTimeMs) {
        public static final Run NONE = new Run(0, MAX_LEG_TIME_MS);
    }

    /**
     * A driver's total over the counting legs.
     *
     * @param points   the sum of the counting legs' points
     * @param laps     the laps over the counting legs
     * @param timeMs   the time over the counting legs, in milliseconds
     * @param legPoints the points in each leg, in leg order
     * @param counted  whether each leg counted towards the total, in leg order
     */
    public record Standing(long entryId, int points, int laps, long timeMs, List<Integer> legPoints, List<Boolean> counted) {}

    /**
     * Ranks the drivers over the legs that have been run.
     *
     * @param legs        each finished leg's runs by entry id, in leg order
     * @param drivers     everyone in the final, which includes anyone who did not run a leg
     * @param legsCounted how many legs count (0 for all of them); never more than the legs run so far
     * @return the drivers best first
     */
    public static List<Standing> rank(List<Map<Long, Run>> legs, Collection<Long> drivers, int legsCounted) {
        int didNotRun = drivers.size() + 1;
        Map<Long, List<Integer>> pointsBy = new HashMap<>();
        Map<Long, List<Run>> runsBy = new HashMap<>();
        for (Long driver : drivers) {
            pointsBy.put(driver, new ArrayList<>());
            runsBy.put(driver, new ArrayList<>());
        }
        for (Map<Long, Run> leg : legs) {
            List<Map.Entry<Long, Run>> ran = leg.entrySet().stream()
                    .filter(e -> pointsBy.containsKey(e.getKey()) && e.getValue().laps() > 0)
                    .sorted(Comparator
                            .comparingInt((Map.Entry<Long, Run> e) -> e.getValue().laps()).reversed()
                            .thenComparingLong(e -> e.getValue().totalTimeMs())
                            .thenComparing(Map.Entry::getKey))
                    .toList();
            Map<Long, Integer> place = new HashMap<>();
            for (int i = 0; i < ran.size(); i++) {
                place.put(ran.get(i).getKey(), i + 1);
            }
            for (Long driver : drivers) {
                pointsBy.get(driver).add(place.getOrDefault(driver, didNotRun));
                runsBy.get(driver).add(leg.getOrDefault(driver, Run.NONE));
            }
        }
        List<Standing> standings = new ArrayList<>();
        for (Long driver : drivers) {
            standings.add(standing(driver, pointsBy.get(driver), runsBy.get(driver), legsCounted));
        }
        standings.sort(Comparator.comparingInt(Standing::points)
                .thenComparing(FinalLegScoring::byCountingLegs)
                .thenComparing(Comparator.comparingInt(Standing::laps).reversed())
                .thenComparingLong(Standing::timeMs)
                .thenComparingLong(Standing::entryId));
        return standings;
    }

    private static Standing standing(long entryId, List<Integer> points, List<Run> runs, int legsCounted) {
        List<Integer> bestFirst = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            bestFirst.add(i);
        }
        bestFirst.sort(Comparator.<Integer>comparingInt(points::get)
                .thenComparing(Comparator.<Integer>comparingInt(i -> runs.get(i).laps()).reversed())
                .thenComparingLong(i -> runs.get(i).totalTimeMs()));
        int counting = legsCounted > 0 ? Math.min(legsCounted, points.size()) : points.size();
        boolean[] counted = new boolean[points.size()];
        int total = 0;
        int laps = 0;
        long time = 0;
        for (int k = 0; k < counting; k++) {
            int leg = bestFirst.get(k);
            counted[leg] = true;
            total += points.get(leg);
            laps += runs.get(leg).laps();
            time += Math.min(runs.get(leg).totalTimeMs(), MAX_LEG_TIME_MS);
        }
        List<Boolean> flags = new ArrayList<>();
        for (boolean c : counted) {
            flags.add(c);
        }
        return new Standing(entryId, total, laps, time, List.copyOf(points), flags);
    }

    /** The counting legs' points best first, compared one by one: 1 + 3 beats 2 + 2. */
    private static int byCountingLegs(Standing a, Standing b) {
        List<Integer> left = countingPoints(a);
        List<Integer> right = countingPoints(b);
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int compared = Integer.compare(left.get(i), right.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static List<Integer> countingPoints(Standing standing) {
        List<Integer> counting = new ArrayList<>();
        for (int i = 0; i < standing.legPoints().size(); i++) {
            if (standing.counted().get(i)) {
                counting.add(standing.legPoints().get(i));
            }
        }
        counting.sort(Comparator.naturalOrder());
        return counting;
    }
}
