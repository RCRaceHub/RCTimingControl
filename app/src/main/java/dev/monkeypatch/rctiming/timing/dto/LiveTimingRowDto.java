package dev.monkeypatch.rctiming.timing.dto;

/**
 * One car's line in a live timing update, broadcast on {@code /topic/race/{raceId}/timing} and sent to the
 * boards. Times are in milliseconds. {@code lastPassingTimeMs} is a wall-clock time (UTC milliseconds), not a lap
 * time. The gap fields are null where no time gap means anything: for the leader, and for a car on a different lap
 * from the one it is measured against, which shows as {@code lapsDown} or {@code intervalLapsDown} instead.
 */
public record LiveTimingRowDto(
        long entryId,
        String driverName,
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
        /** The car has taken the flag: its time on the track is up and its last lap has counted. */
        boolean finished
) {
}
