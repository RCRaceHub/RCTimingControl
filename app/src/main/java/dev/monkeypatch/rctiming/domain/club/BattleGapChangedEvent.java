package dev.monkeypatch.rctiming.domain.club;

/**
 * Published after the club's battle gap has been saved (#505), so live timing can apply it to races in progress
 * rather than from the next race. {@code gapMs} is in milliseconds; 0 switches battles off.
 */
public record BattleGapChangedEvent(int gapMs) {
}
