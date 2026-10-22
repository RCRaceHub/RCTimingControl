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
 *
 * <p>{@code bestHeatsCount} is how many of the qualifying rounds count when ranked round by round (0 counts them
 * all). {@code splitTiedPlaces} stops cars with identical laps and time sharing a place in a round, so the Nth car
 * in order scores N points. {@code brcaQualifying} applies the BRCA rules to round by round qualifying: a driver
 * with no laps scores the class's entries plus one, and a tie on points goes to the best single counting round,
 * then the second best, then the summed laps and the summed time of the counting rounds. All three are off (0 or
 * false) in configs saved before they existed, which keeps their behaviour.
 *
 * <p>{@code maxRaceWindowSeconds} is the longest a timed race may run, counted from the start of the race (from the
 * last car's start in a stagger start), whatever the cars are doing: when it is reached the race is finished and a
 * car that has not completed its last lap is left with the laps it had before the heat's duration ran out. 0 means
 * no limit, and the track's max last lap wait is the only backstop.
 *
 * <p>{@code carsPerFinal} is the cars on each final's grid (0 splits the field evenly), and {@code fillTopFirst}
 * fills the finals from the top, A with ranks 1 to {@code carsPerFinal}, B with the next block, and the lowest
 * final with whoever is left, instead of filling the lowest final first. Neither makes drivers bump up.
 *
 * <p>{@code finalLegs} is how many races the A final is run in (0 or 1 for one race) and {@code legsCounted} how many of
 * a driver's legs count towards their place (0 for all of them): in each leg a driver scores their finishing place and
 * the lowest total over the counting legs wins. Lower finals are always a single race.
 *
 * <p>{@code scoringSystemId} is the race scoring system (see {@code domain/scoring}) the format was set from, or null
 * when its scoring options were set by hand. When it is set, saving the template and giving it to an event class
 * copies the system's values over {@code bestHeatsCount}, {@code splitTiedPlaces}, {@code brcaQualifying}, {@code
 * maxRaceWindowSeconds}, {@code finalLegs}, {@code legsCounted}, {@code carsPerFinal} and {@code fillTopFirst}, so the
 * scoring code keeps reading those fields and an event class keeps its own copy.
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
    int staggerIntervalSeconds,
    int bestHeatsCount,
    boolean splitTiedPlaces,
    boolean brcaQualifying,
    int maxRaceWindowSeconds,
    int carsPerFinal,
    boolean fillTopFirst,
    int finalLegs,
    int legsCounted,
    Long scoringSystemId
) implements RaceFormatConfig {

    /** A config set by hand, with no scoring system. */
    public PointsFinalsConfig(int qualifyingHeats, int finalsCount, int finalDurationMinutes,
                              int heatDurationMinutes, StartType qualifyingStartType, StartType finalsStartType,
                              QualifyingType qualifyingType, int racePaddingMinutes, int staggerIntervalSeconds,
                              int bestHeatsCount, boolean splitTiedPlaces, boolean brcaQualifying,
                              int maxRaceWindowSeconds, int carsPerFinal, boolean fillTopFirst, int finalLegs,
                              int legsCounted) {
        this(qualifyingHeats, finalsCount, finalDurationMinutes, heatDurationMinutes, qualifyingStartType,
                finalsStartType, qualifyingType, racePaddingMinutes, staggerIntervalSeconds, bestHeatsCount,
                splitTiedPlaces, brcaQualifying, maxRaceWindowSeconds, carsPerFinal, fillTopFirst, finalLegs,
                legsCounted, null);
    }

    /** A config without the round by round scoring options: every round counts and the rules are today's. */
    public PointsFinalsConfig(int qualifyingHeats, int finalsCount, int finalDurationMinutes,
                              int heatDurationMinutes, StartType qualifyingStartType, StartType finalsStartType,
                              QualifyingType qualifyingType, int racePaddingMinutes, int staggerIntervalSeconds) {
        this(qualifyingHeats, finalsCount, finalDurationMinutes, heatDurationMinutes, qualifyingStartType,
                finalsStartType, qualifyingType, racePaddingMinutes, staggerIntervalSeconds, 0, false, false, 0, 0, false, 0, 0,
                null);
    }
}
