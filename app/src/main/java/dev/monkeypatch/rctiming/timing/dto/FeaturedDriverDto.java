package dev.monkeypatch.rctiming.timing.dto;

/** The frame on {@code /topic/race/{raceId}/featured}: the car the race director wants the overlay to feature (#533). */
public record FeaturedDriverDto(long raceId, long entryId) {
}
