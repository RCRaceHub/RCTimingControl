package dev.monkeypatch.rctiming.domain.championship;

/**
 * Which rounds of an event score points in a championship (CHAMP-06): {@link #QUALIFYING} scores the driver's best
 * qualifying place, {@link #FINALS} the best final place, {@link #BOTH} adds the two. Practice never scores.
 */
public enum ScoringSource {
    QUALIFYING,
    FINALS,
    BOTH
}
