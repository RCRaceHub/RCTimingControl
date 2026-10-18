package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single timed race: it runs for {@code durationMinutes} and has one {@code startType} for every round.
 *
 * <p>Durations are in minutes. {@code racePaddingMinutes} is kept so saved configs still load and is not
 * read (the gap between races is the event's auto run gap). {@code staggerIntervalSeconds} is the seconds between
 * car number calls in a stagger start and {@code qualifyingType} is how qualifying is ranked (see
 * {@link QualifyingType}). Unknown JSON properties are ignored, so older saved configs still load.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimedRaceConfig(
    int durationMinutes,
    StartType startType,
    QualifyingType qualifyingType,
    int racePaddingMinutes,
    int staggerIntervalSeconds
) implements RaceFormatConfig {}
