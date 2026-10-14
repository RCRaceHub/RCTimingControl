package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Qualifying heats, then {@code finalsCount} finals scored on points (FORMAT-04). Heats run for
 * {@code heatDurationMinutes} with {@code qualifyingStartType}; finals run for {@code finalDurationMinutes} with
 * {@code finalsStartType}.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is the gap between successive races and
 * {@code staggerIntervalSeconds} the seconds between car number calls in a stagger start; {@code qualifyingType}
 * is how qualifying is ranked (see {@link QualifyingType}). Unknown JSON properties are ignored, so older saved
 * configs still load.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PointsFinalsConfig(
    int qualifyingHeats,
    int finalsCount,
    int finalDurationMinutes,
    int heatDurationMinutes,
    StartType qualifyingStartType,
    StartType finalsStartType,
    QualifyingType qualifyingType,
    int racePaddingMinutes,
    int staggerIntervalSeconds
) implements RaceFormatConfig {}
