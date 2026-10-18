package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shikabane racing: every heat is a timed race in which a car that flips, breaks down or stalls stays where it
 * stopped as an obstacle (a "corpse") and is eliminated. Race control marks eliminations and zombie revivals by
 * hand; the order of elimination sets each car's heat penalty points and the lowest total over the heats wins.
 *
 * <p>Every heat runs for {@code heatDurationMinutes} with {@code startType}; {@code staggerIntervalSeconds} is the
 * seconds between car number calls in a stagger start. {@code falseStartPenaltyPoints} is added to a car that
 * jumps the start and {@code zombieBonusPoints} is taken off a driver who revives a dead car. Unknown JSON
 * properties are ignored, so older saved configs still load.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShikabaneConfig(
    int heatDurationMinutes,
    StartType startType,
    int staggerIntervalSeconds,
    int falseStartPenaltyPoints,
    int zombieBonusPoints
) implements RaceFormatConfig {}
