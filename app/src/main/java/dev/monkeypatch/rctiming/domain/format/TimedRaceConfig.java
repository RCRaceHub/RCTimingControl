package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single timed race: it runs for {@code durationMinutes} and starts with {@code startType}.
 * {@code finalsStartType} is the start of a final round; when it is null (older saved configs) finals use
 * {@code startType} too.
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
    int staggerIntervalSeconds,
    StartType finalsStartType
) implements RaceFormatConfig {

    /** A config with no separate finals start: finals use {@code startType}. */
    public TimedRaceConfig(int durationMinutes, StartType startType, QualifyingType qualifyingType,
                           int racePaddingMinutes, int staggerIntervalSeconds) {
        this(durationMinutes, startType, qualifyingType, racePaddingMinutes, staggerIntervalSeconds, null);
    }
}
