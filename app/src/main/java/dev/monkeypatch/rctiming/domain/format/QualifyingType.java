package dev.monkeypatch.rctiming.domain.format;

/**
 * How a class's qualifying heats are ranked. {@link #ROUND_BY_ROUND} scores low points per round, best X counting;
 * {@link #FTQ}, {@link #FASTEST_LAP} and {@link #CONSECUTIVE_LAPS} currently all rank by total laps and then best lap.
 * See {@code docs/scoring.md}.
 */
public enum QualifyingType {
    FTQ, ROUND_BY_ROUND, FASTEST_LAP, CONSECUTIVE_LAPS
}
