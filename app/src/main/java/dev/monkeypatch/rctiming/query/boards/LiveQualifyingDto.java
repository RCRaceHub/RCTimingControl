package dev.monkeypatch.rctiming.query.boards;

import java.util.List;

/**
 * The live qualifying table of the class on track: its low-points order as it would stand if the running heat
 * ended now (#480). Answer of {@code GET /api/v1/boards/live-qualifying}; {@code eventId} and {@code eventName} are
 * null when no event is found, and {@code rows} is empty when there is no qualifying heat to show or the class is
 * not ranked by low points.
 *
 * <p>{@code phase} is {@code LIVE} while the heat runs (the order is a projection), {@code FINISHED} after it ends
 * (the order is settled, and {@code previousPosition} still shows who moved), and {@code NONE} when there is no
 * qualifying heat to show.
 *
 * @param raceId    the heat being shown, null for {@code NONE}
 * @param raceLabel the heat's label, such as "Qualifying 2 — Stock Buggy — Heat 1"
 * @param className the class the table is for
 * @param rows      best first; names are as the public may see them (#265)
 */
public record LiveQualifyingDto(Long eventId, String eventName, String phase, Long raceId, String raceLabel,
                                String className, List<Row> rows) {

    /**
     * One driver. {@code previousPosition} is the place before the heat counted, null for a driver with no earlier
     * result. {@code live} is true for a driver in the heat on track, {@code waiting} for a driver whose heat in the
     * same round has not run yet. {@code points} is the low-points total, {@code bestLapMs} null with no timed lap.
     */
    public record Row(int position, Integer previousPosition, long entryId, String driverName, String carNumber,
                      Integer points, int lapsCompleted, Long bestLapMs, boolean live, boolean waiting) {}
}
