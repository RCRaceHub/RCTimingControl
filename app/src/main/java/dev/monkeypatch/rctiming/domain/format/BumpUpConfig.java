package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Qualifying heats then bump-up finals (FORMAT-02, FORMAT-12): {@code qualifyingHeats} heats of
 * {@code heatDurationMinutes}, of which the best {@code bestHeatsCount} are meant to count. {@code gridSize} is the
 * racers per final and {@code bumpSpots} the places in each higher final filled by the final below. Heats take
 * {@code qualifyingStartType} and finals {@code finalsStartType}. The number of finals comes from the entry count at
 * event time, not from the config.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is the gap between successive races and
 * {@code staggerIntervalSeconds} the seconds between car number calls in a stagger start; {@code qualifyingType}
 * is how qualifying is ranked (see {@link QualifyingType}). Unknown JSON properties are ignored, so older saved
 * configs still load.
 *
 * <p>Only {@code heatDurationMinutes} and the two start types are read by the backend so far. Round generation
 * takes cars per final and bump count from its request ({@code BumpUpSeedingService}), not from this record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BumpUpConfig(
    int qualifyingHeats,
    int heatDurationMinutes,
    int bestHeatsCount,
    int gridSize,
    int bumpSpots,
    StartType qualifyingStartType,
    StartType finalsStartType,
    QualifyingType qualifyingType,
    int racePaddingMinutes,
    int staggerIntervalSeconds
) implements RaceFormatConfig {}
