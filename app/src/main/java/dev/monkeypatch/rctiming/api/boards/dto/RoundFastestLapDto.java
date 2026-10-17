package dev.monkeypatch.rctiming.api.boards.dto;

/**
 * The fastest lap so far in the finished heats of a race's round and class, for the streaming overlay's ticker.
 * Both fields are null when no other heat has finished. Answer of
 * {@code GET /api/v1/boards/races/{raceId}/round-fastest-lap}; {@code lapMs} is the lap time in milliseconds.
 */
public record RoundFastestLapDto(Long lapMs, String driverName) {}
