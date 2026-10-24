package dev.monkeypatch.rctiming.resultsexport;

/**
 * Where a club publishes its results (#319). {@link #SITE} is the results site for clubs without RaceHub:
 * static pages from {@link ResultsSiteService}, written to a folder or downloaded as a zip. It queues nothing in the
 * outbox.
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
