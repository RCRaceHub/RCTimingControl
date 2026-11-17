package dev.monkeypatch.rctiming.timing.split;

import java.util.List;

/**
 * Published by {@link SectorTimingService} when a car finishes a lap that has every split (#672).
 *
 * @param raceId            the running race
 * @param transponderNumber the transponder that did the lap, as text
 * @param lapNumber         1 for the lap that ended at the car's first crossing after its start crossing
 * @param sectorMicros      one entry per sector, in track order: start/finish to split 1, split 1 to split 2, and so on
 *                          up to the last split to the finish; the sum is the lap time as seen by the loops
 */
public record SectorTimesEvent(long raceId, String transponderNumber, int lapNumber, List<Long> sectorMicros) {
}
