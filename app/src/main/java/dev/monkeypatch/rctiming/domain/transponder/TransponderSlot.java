package dev.monkeypatch.rctiming.domain.transponder;

/** Which of an entry's transponders a swap changes (L6, L11, #482). The third and fourth are optional extras. */
public enum TransponderSlot {
    PRIMARY,
    SECONDARY,
    THIRD,
    FOURTH
}
