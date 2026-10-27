package dev.monkeypatch.rctiming.query.boards;

import java.util.List;

/**
 * The live qualifying tables of the class on track (#480): the overall low-points order as it would stand if the
 * running heat ended now, and the order inside the round that heat belongs to. Answer of
 * {@code GET /api/v1/boards/live-qualifying}; {@code eventId} and {@code eventName} are null when no event is found,
 * and {@code rows} is empty when there is no qualifying heat to show or the class is not ranked by low points.
 *
 * <p>{@code phase} is {@code LIVE} while the heat runs (the order is a projection), {@code FINISHED} after it ends
 * (the order is settled, and {@code previousPosition} still shows who moved), and {@code NONE} when there is no
 * qualifying heat to show.
 *
 * <p>For a practice heat the tables are ranked by the event's practice ranking instead of low points: {@code
 * rankingLabel} names it ("Fastest lap", "Best 3 laps in a row"), {@code bestLapMs} of every row is the ranked time,
 * {@code points} and {@code totalTimeMs} are null, and {@code roundRows} is the heat's round (one practice session).
 *
 * <p>For a final, {@code roundType} is {@code FINAL}: {@code roundRows} is the leg on track (or the one that just
 * ended) and {@code rows} the final's combined order over its legs, with the leg on track counted as if it ended now
 * and {@code points} the total (lowest wins). A final of one leg has {@code rows} only, the leg's own order.
 * {@code heading}, {@code roundTitle} and {@code overallTitle} then name them ("Buggy A final", "Leg 2 of 3",
 * "Combined"); they are null for practice and qualifying, where the board words them itself.
 *
 * @param roundType   {@code QUALIFIER}, {@code PRACTICE} or {@code FINAL}, null for {@code NONE}
 * @param heading     the board's title for a final, null otherwise
 * @param roundTitle  the heading over {@code roundRows} for a final, null otherwise
 * @param overallTitle the heading over {@code rows} for a final, null otherwise
 * @param rankingLabel what {@code bestLapMs} holds for a practice heat, null for qualifying
 * @param raceId      the heat being shown, null for {@code NONE}
 * @param raceLabel   the heat's label, such as "Qualifying 2 — Stock Buggy — Heat 1"
 * @param className   the class the table is for
 * @param roundNumber the round the heat belongs to, null for {@code NONE}
 * @param rows        the overall order, best first; names are as the public may see them (#265)
 * @param roundRows   the order inside the heat's round, best first: laps, then total time, over the round's heats
 *                    counted so far. Empty for a class whose heats are separate races (Shikabane).
 */
public record LiveQualifyingDto(Long eventId, String eventName, String phase, Long raceId, String raceLabel,
                                String className, Integer roundNumber, String roundType, String rankingLabel,
                                String heading, String roundTitle, String overallTitle,
                                List<Row> rows, List<RoundRow> roundRows) {

    /** The answer when there is nothing to show. */
    public static LiveQualifyingDto none(Long eventId, String eventName) {
        return new LiveQualifyingDto(eventId, eventName, "NONE", null, null, null, null, null, null, null, null,
                null, List.of(), List.of());
    }

    /**
     * One driver. {@code previousPosition} is the place before the heat counted, null for a driver with no earlier
     * result. {@code live} is true for a driver in the heat on track, {@code waiting} for a driver whose heat in the
     * same round has not run yet. {@code points} is the low-points total, {@code bestLapMs} null with no timed lap.
     */
    public record Row(int position, Integer previousPosition, long entryId, String driverName, String carNumber,
                      Integer points, int lapsCompleted, Long bestLapMs, boolean live, boolean waiting) {}

    /**
     * One driver in the round's own order. {@code previousPosition} is the place before the heat counted, null for a
     * driver not yet in the round. {@code totalTimeMs} and {@code bestLapMs} are null with no laps or no timed lap.
     */
    public record RoundRow(int position, Integer previousPosition, long entryId, String driverName, String carNumber,
                           int lapsCompleted, Long totalTimeMs, Long bestLapMs, boolean live) {}
}
