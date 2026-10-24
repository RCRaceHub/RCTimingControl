package dev.monkeypatch.rctiming.domain.boardview;

/** What a panel of a saved board view shows. The page that draws a view has one component for each. */
public enum BoardPanelType {
    /** The race on track with its live timing table and lap bars. */
    LIVE_TIMING,
    /** The live qualifying table of the class on track (see {@code LiveQualifyingService}); takes a number of places. */
    LIVE_TOP,
    /** The next race, the countdown to its start, its drivers and the marshals for it. */
    NEXT_RACE,
    /** The drivers marshalling the race on track. */
    MARSHALS,
    /** The result of the race that finished last. */
    LAST_RESULT
}
