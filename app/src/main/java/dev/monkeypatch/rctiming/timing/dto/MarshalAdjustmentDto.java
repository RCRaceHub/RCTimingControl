package dev.monkeypatch.rctiming.timing.dto;

/**
 * A marshal lap adjustment as broadcast on {@code /topic/race/{raceId}/marshal}. {@code lapDelta} is +1 or -1;
 * {@code adjustedAtEpochMs} is UTC milliseconds.
 */
public record MarshalAdjustmentDto(
        long raceId,
        long entryId,
        String transponderNumber,
        int lapDelta,
        String actingUserName,
        long adjustedAtEpochMs
) {
}
