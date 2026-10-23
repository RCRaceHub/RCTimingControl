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
            Integer championshipPoints
    ) {
        /** A row with no points, as a race is stored. */
        public ResultRow(int position, long entryId, Long competitorId, String driverName, String carNumber,
                         int lapsCompleted, long totalTimeMs, Long bestLapMs, Long gapToLeaderMs) {
            this(position, entryId, competitorId, driverName, carNumber, lapsCompleted, totalTimeMs, bestLapMs,
                    gapToLeaderMs, null, null);
        }

        /** This row with the given round points. */
        public ResultRow withPoints(Integer points) {
            return new ResultRow(position, entryId, competitorId, driverName, carNumber, lapsCompleted,
                    totalTimeMs, bestLapMs, gapToLeaderMs, points, championshipPoints);
        }

        /** This row with the given championship points. */
        public ResultRow withChampionshipPoints(Integer championshipPoints) {
            return new ResultRow(position, entryId, competitorId, driverName, carNumber, lapsCompleted,
                    totalTimeMs, bestLapMs, gapToLeaderMs, points, championshipPoints);
        }
    }

    public record PositionAtLap(int lapNumber, long entryId, int position, Long lapTimeMs) {}

    public record ClubBrandingDto(String clubName, String logoUrl) {}
}
