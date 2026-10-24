package dev.monkeypatch.rctiming.timing.battle;

/**
 * The gap between a car and the one behind it in a {@link BattleGroupDto}, in milliseconds as of the car behind's last
 * crossing. {@code ratePerLapMs} is how much the gap changes each of that car's laps (negative is closing), null
 * until two laps of the pair have been seen. {@code gainingFast} is a closing rate of 300 ms a lap or more.
 */
public record BattleLinkDto(long gapMs, Long ratePerLapMs, BattleTrend trend, boolean gainingFast) {
}
