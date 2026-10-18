package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Qualifying heats, then {@code finalsCount} finals scored on points (FORMAT-04). Heats run for
 * {@code heatDurationMinutes} with {@code qualifyingStartType}; finals run for {@code finalDurationMinutes} with
 * {@code finalsStartType}. {@code qualifyingHeats} and {@code finalsCount} are the round generator's starting numbers
 * in the browser form, which the official can change; the backend takes the counts from the generation request.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is kept so saved configs still load and is not
 * read (the gap between races is the event's auto run gap). {@code staggerIntervalSeconds} is the seconds between
 * car number calls in a stagger start and {@code qualifyingType} is how qualifying is ranked (see
 * {@link QualifyingType}). Unknown JSON properties are ignored, so older saved configs still load.
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
