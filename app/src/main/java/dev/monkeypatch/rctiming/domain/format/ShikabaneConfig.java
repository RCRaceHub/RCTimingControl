package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shikabane racing: every heat is a timed race in which a car that flips, breaks down or stalls stays where it
 * stopped as an obstacle (a "corpse") and is eliminated. Race control marks eliminations and zombie revivals by
 * hand; the order of elimination sets each car's heat penalty points and the lowest total over the heats wins.
 *
 * <p>A heat ends after {@code heatLaps} laps when that is more than 0 (each car takes the flag on its last lap and
 * the heat is over when every car that crossed the line has), otherwise it runs for {@code heatDurationMinutes}.
 * Either way it starts with {@code startType} (mains with {@code finalsStartType}, or {@code startType} when that
 * is null in an older saved config); {@code staggerIntervalSeconds} is the
 * seconds between car number calls in a stagger start. {@code falseStartPenaltyPoints} is added to a car that
 * jumps the start and {@code zombieBonusPoints} is taken off a driver who revives a dead car.
 *
 * <p>With {@code mains} above 0 the heats are followed by that many mains (A, then B, then C) instead of ending the
 * event: the heat totals seed them, the lowest main takes the worst drivers, and each main holds
 * {@code mainGridSize} cars. The first {@code promotionSpots} places of a lower main move up into the main above it
 * when it finishes. With {@code zombieRound} (and at least two mains) a zombie round runs after the lower mains and
 * before the A main: every driver not already in the A main races once more, and the winner takes a place in it.
 * Unknown JSON properties are ignored, so older saved configs still load (and have no mains).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShikabaneConfig(
    int heatDurationMinutes,
    StartType startType,
    int staggerIntervalSeconds,
    int falseStartPenaltyPoints,
    int zombieBonusPoints,
    int heatLaps,
    int mains,
    int mainGridSize,
    int promotionSpots,
    boolean zombieRound,
    StartType finalsStartType
) implements RaceFormatConfig {

    /** A config with no separate finals start: mains use {@code startType}. */
    public ShikabaneConfig(int heatDurationMinutes, StartType startType, int staggerIntervalSeconds,
                           int falseStartPenaltyPoints, int zombieBonusPoints, int heatLaps, int mains,
                           int mainGridSize, int promotionSpots, boolean zombieRound) {
        this(heatDurationMinutes, startType, staggerIntervalSeconds, falseStartPenaltyPoints, zombieBonusPoints,
                heatLaps, mains, mainGridSize, promotionSpots, zombieRound, null);
    }
}
