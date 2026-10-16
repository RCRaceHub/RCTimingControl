package dev.monkeypatch.rctiming.domain.race;

/** What a marshal penalty costs the driver who missed their duty. */
public enum MarshalPenaltyKind {
    /** Their best qualifying round does not count, so the next best takes its place. */
    QUALIFYING_SCORE,
    /** A lap off their result in the first final they race. */
    FINALS_LAP
}
