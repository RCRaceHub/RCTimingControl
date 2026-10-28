package dev.monkeypatch.rctiming.domain.race;

import java.time.Instant;
import java.util.List;

/**
 * A finished race's result as it is stored in a {@link ResultSnapshot} (see {@link ResultSnapshotJson}) and
 * served to race control, the boards and the public results. It lives with the snapshot because the domain,
 * the services and the read side all build or read it.
 */
public record ResultSnapshotDto(
        long raceId,
        String raceLabel,
        Instant finishedAt,
        List<ResultRow> positions,
        List<PositionAtLap> lapHistory,
        ClubBrandingDto clubBranding
) {
    /**
     * One driver's line in a result. {@code points} is the driver's points for the race when its round is scored by
     * points (a qualifying heat of a low-points class), and {@code championshipPoints} is what the driver's place in
     * the event's finals is worth in the championship the event belongs to (a final of a class that scores). Neither
     * is stored: both are filled in when a result is read ({@code ResultSnapshotQuery}) and are null otherwise.
     *
     * <p>{@code dnf} marks a car that was still on the track when the race hit its maximum race window and so did not
     * take the flag (#428). It keeps its laps and its place; the flag is only a label. Results stored before it
     * existed read as false.
     */
    public record ResultRow(
            int position,
            long entryId,
            Long competitorId,        // L5: the driver; null in snapshots written before competitors existed
            String driverName,        // competitor display name at the time the race finished
            String carNumber,
            int lapsCompleted,
            long totalTimeMs,
            Long bestLapMs,
            Long gapToLeaderMs,
            Integer points,
            Integer championshipPoints,
            boolean dnf
    ) {
        /** A row with no points and not marked DNF, as a race is stored. */
        public ResultRow(int position, long entryId, Long competitorId, String driverName, String carNumber,
                         int lapsCompleted, long totalTimeMs, Long bestLapMs, Long gapToLeaderMs) {
            this(position, entryId, competitorId, driverName, carNumber, lapsCompleted, totalTimeMs, bestLapMs,
                    gapToLeaderMs, null, null, false);
        }

        /** A row with points, not marked DNF. */
        public ResultRow(int position, long entryId, Long competitorId, String driverName, String carNumber,
                         int lapsCompleted, long totalTimeMs, Long bestLapMs, Long gapToLeaderMs, Integer points,
                         Integer championshipPoints) {
            this(position, entryId, competitorId, driverName, carNumber, lapsCompleted, totalTimeMs, bestLapMs,
                    gapToLeaderMs, points, championshipPoints, false);
        }

        /** This row with the given round points. */
        public ResultRow withPoints(Integer points) {
            return new ResultRow(position, entryId, competitorId, driverName, carNumber, lapsCompleted,
                    totalTimeMs, bestLapMs, gapToLeaderMs, points, championshipPoints, dnf);
        }

        /** This row with the given championship points. */
        public ResultRow withChampionshipPoints(Integer championshipPoints) {
            return new ResultRow(position, entryId, competitorId, driverName, carNumber, lapsCompleted,
                    totalTimeMs, bestLapMs, gapToLeaderMs, points, championshipPoints, dnf);
        }

        /** This row marked, or not marked, as a car that did not finish. */
        public ResultRow withDnf(boolean dnf) {
            return new ResultRow(position, entryId, competitorId, driverName, carNumber, lapsCompleted,
                    totalTimeMs, bestLapMs, gapToLeaderMs, points, championshipPoints, dnf);
        }
    }

    /**
     * A car's place at the end of a lap, with that lap's time. Lap 0 is the grid: the car's starting place, with no
     * lap time (results stored before it existed have none).
     */
    public record PositionAtLap(int lapNumber, long entryId, int position, Long lapTimeMs) {}

    public record ClubBrandingDto(String clubName, String logoUrl) {}
}
