package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.List;

/**
 * One class's finals line-up for the race control panel: whether the finals can be calculated yet, the cars per
 * final to offer, and who is on each final's grid.
 *
 * @param qualifyingComplete  every qualifying heat is finished and not all were abandoned, so the finals can be
 *                            calculated
 * @param calculated          at least one final has drivers on its grid
 * @param started             a final has left PENDING, after which the line-ups are locked
 * @param defaultCarsPerFinal the largest qualifying heat, offered as the cars per final so the finals are as full
 *                            as the heats were (marshalling is sized the same way)
 */
public record FinalsLineupDto(
        long eventClassId,
        String className,
        boolean qualifyingComplete,
        boolean calculated,
        boolean started,
        int defaultCarsPerFinal,
        List<Final> finals
) {
    /**
     * One final. {@code bumpSlots} places at the back of the grid are still to be filled by the winners of the
     * final below. {@code editable} is false once the final has been called or started.
     */
    public record Final(long raceId, String letter, String status, int bumpSlots, boolean editable,
                        List<Driver> drivers) {}

    /**
     * A driver on a final's grid. {@code qualifyingPosition} is the driver's place in the overall qualifying order,
     * null when they have no qualifying result.
     */
    public record Driver(long entryId, String driverName, Integer gridPosition, Integer carNumber,
                         Integer qualifyingPosition) {}
}
