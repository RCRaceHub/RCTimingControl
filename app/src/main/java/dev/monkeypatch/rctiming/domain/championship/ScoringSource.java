package dev.monkeypatch.rctiming.domain.championship;

/**
 * The old "scoring source" setting of a championship (CHAMP-06). It no longer does anything: a championship always
 * scores the event's overall result, the finals or, for a class with none, its overall qualifying order. Kept only
 * because the {@code championships.scoring_source} column still exists, so old rows read back; new championships
 * keep the default {@link #FINALS} and the API does not show it.
 */
public enum ScoringSource {
    QUALIFYING,
    FINALS,
    BOTH
}
