package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.List;

/**
 * The combined result of a class's A final when it is run in legs (#427). Officials only: the names are the full ones
 * the legs' results were stored with.
 *
 * @param eventClassId the class
 * @param className    the racing class's name
 * @param legs         how many legs the final has
 * @param legsFinished how many have finished and have a result
 * @param legsCounted  how many of a driver's legs count, 0 for all of them
 * @param complete     whether every leg has finished, so the order is the final's result
 * @param rows         the drivers best first
 */
public record FinalLegsDto(Long eventClassId, String className, int legs, int legsFinished, int legsCounted,
                           boolean complete, List<Row> rows) {

    /**
     * One driver's line.
     *
     * @param position    1 for the best
     * @param points      the total over the counting legs, lowest wins
     * @param laps        the laps over the counting legs
     * @param timeMs      the time over the counting legs in milliseconds
     * @param legPoints   the points in each leg run so far
     * @param counted     whether each of those legs counted
     */
    public record Row(int position, long entryId, String driverName, int points, int laps, long timeMs,
                      List<Integer> legPoints, List<Boolean> counted) {}
}
