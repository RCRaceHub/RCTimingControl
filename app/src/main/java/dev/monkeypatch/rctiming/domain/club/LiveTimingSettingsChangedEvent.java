package dev.monkeypatch.rctiming.domain.club;

/**
 * Published after the club's live timing thresholds have been saved (#505), so live timing can apply them to races
 * in progress rather than from the next race.
 */
public record LiveTimingSettingsChangedEvent(LiveTimingSettings settings) {
}
