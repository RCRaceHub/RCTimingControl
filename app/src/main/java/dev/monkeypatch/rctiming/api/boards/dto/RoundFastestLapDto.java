package dev.monkeypatch.rctiming.api.boards.dto;

/**
 * The fastest lap so far in the finished heats of a race's round and class, for the streaming overlay's ticker.
 * Both fields are null when no other heat has finished.
 */
public record RoundFastestLapDto(Long lapMs, String driverName) {}
