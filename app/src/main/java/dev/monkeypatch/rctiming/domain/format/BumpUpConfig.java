package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Qualifying heats then bump-up finals (FORMAT-02, FORMAT-12): {@code qualifyingHeats} heats of
 * {@code heatDurationMinutes}, of which the best {@code bestHeatsCount} are meant to count. {@code gridSize} is the
 * racers per final and {@code bumpSpots} the places in each higher final filled by the final below. Heats take
 * {@code qualifyingStartType} and finals {@code finalsStartType}. The number of finals comes from the entry count at
 * event time, not from the config.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is kept so saved configs still load and is not
 * read (the gap between races is the event's auto run gap). {@code staggerIntervalSeconds} is the seconds between
 * car number calls in a stagger start and {@code qualifyingType} is how qualifying is ranked (see
 * {@link QualifyingType}). Unknown JSON properties are ignored, so older saved configs still load.
 *
 * <p>{@code qualifyingHeats} is the round generator's starting number of qualifying rounds in the browser form, which
 * the official can change; the backend does not read it. {@code gridSize} and {@code bumpSpots} lay out the finals
 * ({@code AutoFinalsSeedingService}); {@code bestHeatsCount} is read by qualifying scoring.
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
