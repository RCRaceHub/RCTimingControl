package dev.monkeypatch.rctiming.timing.battle;

/** Which way the gap between two close cars is going, lap on lap. */
public enum BattleTrend {
    /** The car behind is catching the one ahead. */
    CLOSING,
    /** The car ahead is pulling away. */
    GROWING,
    /** The gap is not changing by enough to call. */
    HOLDING,
    /** Too few laps in the battle to tell. */
    UNKNOWN
}
