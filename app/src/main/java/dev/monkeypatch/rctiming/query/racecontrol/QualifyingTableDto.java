package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.List;

/**
 * A class's overall qualifying table: every driver ranked across all the class's finished qualifying heats, which
 * is the order the finals are seeded from.
 *
 * @param heatsFinished qualifying heats finished (an abandoned one counts, but its laps do not)
 * @param heatsTotal    qualifying heats the class has
 * @param finalsStarted whether a final has already left PENDING, after which the finals can't be seeded again
 */
public record QualifyingTableDto(
        long eventClassId,
        String className,
        int heatsFinished,
        int heatsTotal,
        boolean finalsStarted,
        List<Row> rows
) {
    /**
     * {@code bestLapMs} is null for a driver with no timed lap. {@code points} is the low-points total when the
     * class ranks qualifying round by round, else null.
     */
    public record Row(int position, long entryId, String driverName, int lapsCompleted, Long bestLapMs,
                      Integer points) {}
}
