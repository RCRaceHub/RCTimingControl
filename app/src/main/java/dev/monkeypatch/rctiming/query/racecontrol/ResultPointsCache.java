package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.HashMap;
import java.util.Map;

/**
 * The points worked out for a read of several races, so that a caller reading a whole event
 * ({@link EventResultsQuery}) scores each class's rounds and each event's finals once rather than once per race.
 * One instance lives for one read, on one thread, and is not shared.
 */
public final class ResultPointsCache {

    /** Each driver's points in each scored qualifying round of a class: event class id, round id, entry id. */
    public final Map<Long, Map<Long, Map<Long, Integer>>> roundPoints = new HashMap<>();

    /** The championship points of each finalist: event id, race id, entry id. */
    public final Map<Long, Map<Long, Map<Long, Integer>>> finalPoints = new HashMap<>();
}
