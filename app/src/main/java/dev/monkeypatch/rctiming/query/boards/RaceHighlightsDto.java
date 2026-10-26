package dev.monkeypatch.rctiming.query.boards;

/**
 * The standout runs of a heat and of its round, for the overlay's ticker. Answer of
 * {@code GET /api/v1/boards/races/{raceId}/highlights}. Names are as the public may see them (#265). Any mark is
 * null when nobody earned it (no one gained a place, or no car has done enough laps).
 *
 * @param heat  the heat itself, live while it runs and from its stored result once it has finished
 * @param round the best across every finished heat of the round and class and this heat
 */
public record RaceHighlightsDto(Scope heat, Scope round) {

    /**
     * The four standouts of a set of runs. {@code gained} and {@code lost} count places against the grid;
     * {@code avg3} and {@code avg5} are the best average lap, in milliseconds, over three and five laps in a row.
     */
    public record Scope(Mark gained, Mark lost, Mark avg3, Mark avg5) {}

    /** A driver and the number that earned the mark: places, or milliseconds for an average lap. */
    public record Mark(String driverName, long value) {}
}
