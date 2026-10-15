package dev.monkeypatch.rctiming.timing.dto;

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
