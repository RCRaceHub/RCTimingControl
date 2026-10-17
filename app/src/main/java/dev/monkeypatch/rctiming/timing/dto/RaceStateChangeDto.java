package dev.monkeypatch.rctiming.timing.dto;

/**
 * Broadcast on {@code /topic/race/{raceId}/state} after a race command commits; {@code newStatus} is the name of
 * the new {@code RaceStatus}. Clients refetch the race when they get it.
 */
public record RaceStateChangeDto(long raceId, String newStatus) {
}
