package dev.monkeypatch.rctiming.timing.battle;

/**
 * A car in a battle: its place in the running order and the name the boards show. {@code carNumber} is null when
 * none was recorded.
 */
public record BattleCarDto(long entryId, int position, String driverName, String carNumber) {
}
