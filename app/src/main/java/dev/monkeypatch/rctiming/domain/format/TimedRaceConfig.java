package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single timed race: it runs for {@code durationMinutes} and has one {@code startType} for every round.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is the gap between successive races and
 * {@code staggerIntervalSeconds} the seconds between car number calls in a stagger start; {@code qualifyingType}
 * is how qualifying is ranked (see {@link QualifyingType}). Unknown JSON properties are ignored, so older saved
 * configs still load.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimedRaceConfig(
    int durationMinutes,
    StartType startType,
    QualifyingType qualifyingType,
    int racePaddingMinutes,
    int staggerIntervalSeconds
) implements RaceFormatConfig {}
