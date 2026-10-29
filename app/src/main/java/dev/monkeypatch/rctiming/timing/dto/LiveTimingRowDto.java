package dev.monkeypatch.rctiming.timing.dto;

/**
 * One car's line in a live timing update, broadcast on {@code /topic/race/{raceId}/timing} and sent to the
 * boards. Times are in milliseconds. {@code lastPassingTimeMs} is a wall-clock time (UTC milliseconds), not a lap
 * time. {@code carNumber} is the entry's car number for this race, null when none was recorded.
 * The gap fields are null where no time gap means anything: for the leader, and for a car on a different lap
 * from the one it is measured against, which shows as {@code lapsDown} or {@code intervalLapsDown} instead.
 * {@code totalTimeMs} is the time from the race's start (a rolling start's first crossing) to the car's last
 * crossing plus its time penalties, the same figure the result keeps once the race finishes; null before the car has
 * crossed or when the start is not known. {@code timePenaltyMs} is the time the referee has added so far, 0 for none;
 * the order, the gaps and {@code totalTimeMs} already include it. {@code startPosition} is the car's 1-based place on
 * the race's grid, null when it had none; the boards compare it with {@code position} for a places-gained arrow (#479).
 * {@code flyingLap} is true while the car's latest lap is a flying lap (it beat the average of the laps before it, with
 * at least three to beat, the first lap left out), so the boards show a flame until the car's next lap.
 */
public record LiveTimingRowDto(
        long entryId,
        String driverName,
        String carNumber,
        int position,
        int lapsCompleted,
        long lastPassingTimeMs,
        Long lastLapMs,
        Long bestLapMs,
        Long avgLapMs,
        Long overallFastestLapMs,
        int lapsDown,
        int intervalLapsDown,
        Long gapToLeaderMs,
        Long gapToAheadMs,
        Long totalTimeMs,
        /** The car has taken the flag: its time on the track is up and its last lap has counted. */
        boolean finished,
        long timePenaltyMs,
        Integer startPosition,
        boolean flyingLap
) {

    /** A row whose latest lap is not a flying lap. */
    public LiveTimingRowDto(long entryId, String driverName, String carNumber, int position, int lapsCompleted,
                            long lastPassingTimeMs, Long lastLapMs, Long bestLapMs, Long avgLapMs,
                            Long overallFastestLapMs, int lapsDown, int intervalLapsDown, Long gapToLeaderMs,
                            Long gapToAheadMs, Long totalTimeMs, boolean finished, long timePenaltyMs,
                            Integer startPosition) {
        this(entryId, driverName, carNumber, position, lapsCompleted, lastPassingTimeMs, lastLapMs, bestLapMs,
                avgLapMs, overallFastestLapMs, lapsDown, intervalLapsDown, gapToLeaderMs, gapToAheadMs,
                totalTimeMs, finished, timePenaltyMs, startPosition, false);
    }

    /** A row with no grid position. */
    public LiveTimingRowDto(long entryId, String driverName, String carNumber, int position, int lapsCompleted,
                            long lastPassingTimeMs, Long lastLapMs, Long bestLapMs, Long avgLapMs,
                            Long overallFastestLapMs, int lapsDown, int intervalLapsDown, Long gapToLeaderMs,
                            Long gapToAheadMs, Long totalTimeMs, boolean finished, long timePenaltyMs) {
        this(entryId, driverName, carNumber, position, lapsCompleted, lastPassingTimeMs, lastLapMs, bestLapMs,
                avgLapMs, overallFastestLapMs, lapsDown, intervalLapsDown, gapToLeaderMs, gapToAheadMs,
                totalTimeMs, finished, timePenaltyMs, null, false);
    }
}
