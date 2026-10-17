package dev.monkeypatch.rctiming.resultsexport;

/**
 * Where a club publishes its results (#319). {@link #SITE} is the results site for clubs without RaceHub;
 * until it exists, choosing it queues nothing.
 */
public enum ResultsPublishTarget {
    NONE(false, false),
    RACEHUB(true, false),
    SITE(false, true),
    BOTH(true, true);

    private final boolean raceHub;
    private final boolean site;

    ResultsPublishTarget(boolean raceHub, boolean site) {
        this.raceHub = raceHub;
        this.site = site;
    }

    public boolean includesRaceHub() {
        return raceHub;
    }

    public boolean includesSite() {
        return site;
    }
}
