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
     * class ranks qualifying round by round, else null. {@code rounds} is then the driver's place in each finished
     * round, in round order, with whether that round counts towards the total (empty otherwise). {@code carNumber}
     * is the driver's car number in the class's qualifying heats, null when none was recorded. {@code heat} is
     * the heat number when the class's heats are separate overall races (the positions then count from 1 in each
     * heat), else null.
     */
    public record Row(int position, long entryId, String driverName, String carNumber, int lapsCompleted,
                      Long bestLapMs, Integer points, List<RoundPlace> rounds, Integer heat) {}

    /** A driver's place in one finished round; {@code counted} is false for a round that was dropped. */
    public record RoundPlace(int place, boolean counted) {}
}
