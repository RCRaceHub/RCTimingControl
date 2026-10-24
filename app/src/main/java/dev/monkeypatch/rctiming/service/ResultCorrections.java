package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Applies corrections to a race's result as timed (#63): lap changes (marshal lap adjustments and lap
 * penalties) and added time (time penalties), then ranks the drivers again.
 *
 * <p>Pure: no Spring and no database, so the ranking can be tested on its own. Ranking matches live timing:
 * most laps first, then the shortest total time. A driver with no recorded time (no passings, credited laps
 * only by a marshal) goes behind everyone on the same laps who has one.
 */
public final class ResultCorrections {

    private ResultCorrections() {}

    /**
     * @param timed        the result as timed when the race finished, in finishing order
     * @param lapDeltas    laps to add (or, when negative, take off) per entry id
     * @param addedTimeMs  milliseconds to add to the total time per entry id; a car with no recorded time keeps none
     * @return the corrected result in its new order, or {@code timed} unchanged when there is nothing to apply
     */
    public static List<ResultRow> apply(List<ResultRow> timed, Map<Long, Integer> lapDeltas,
                                        Map<Long, Long> addedTimeMs) {
        boolean anyLapChange = lapDeltas.values().stream().anyMatch(d -> d != 0);
        boolean anyAddedTime = addedTimeMs.values().stream().anyMatch(t -> t != 0);
        if (!anyLapChange && !anyAddedTime) {
            return timed;
        }

        List<ResultRow> corrected = new ArrayList<>(timed.size());
        for (ResultRow row : timed) {
            int laps = Math.max(0, row.lapsCompleted() + lapDeltas.getOrDefault(row.entryId(), 0));
            // A car with no recorded time keeps none, so it still ranks behind cars with a time on its laps
            long totalTimeMs = row.totalTimeMs() > 0
                    ? row.totalTimeMs() + addedTimeMs.getOrDefault(row.entryId(), 0L) : row.totalTimeMs();
            corrected.add(new ResultRow(row.position(), row.entryId(), row.competitorId(), row.driverName(),
                    row.carNumber(), laps, totalTimeMs, row.bestLapMs(), null).withDnf(row.dnf()));
        }

        // Ties keep their timed order, which the stable sort preserves
        corrected.sort(Comparator.comparingInt(ResultRow::lapsCompleted).reversed()
                .thenComparingLong(r -> r.totalTimeMs() > 0 ? r.totalTimeMs() : Long.MAX_VALUE));

        List<ResultRow> ranked = new ArrayList<>(corrected.size());
        ResultRow leader = corrected.isEmpty() ? null : corrected.get(0);
        for (int i = 0; i < corrected.size(); i++) {
            ResultRow row = corrected.get(i);
            // As in live timing, a gap is only given between cars on the same lap
            Long gapToLeaderMs = (i == 0 || row.lapsCompleted() != leader.lapsCompleted()
                    || row.totalTimeMs() <= 0 || leader.totalTimeMs() <= 0)
                    ? null : row.totalTimeMs() - leader.totalTimeMs();
            ranked.add(new ResultRow(i + 1, row.entryId(), row.competitorId(), row.driverName(), row.carNumber(),
                    row.lapsCompleted(), row.totalTimeMs(), row.bestLapMs(), gapToLeaderMs).withDnf(row.dnf()));
        }
        return ranked;
    }
}
