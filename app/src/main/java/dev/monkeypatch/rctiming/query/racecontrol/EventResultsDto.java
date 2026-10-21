package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Every finished race of an event with the real driver names and the grid each driver started from, for the
 * officials' printouts (round summary, final results, meeting pack). Unlike the public results, the names are not
 * changed by a competitor's public name setting.
 *
 * @param races the finished races in run order; a race with no stored result is left out
 */
public record EventResultsDto(long eventId, String eventName, LocalDate eventDate, List<Race> races) {

    /**
     * One finished race and where it sat in the event.
     *
     * @param roundType      "PRACTICE", "QUALIFIER" or "FINAL"
     * @param finalLetter    null unless the race is a final
     * @param startPositions the 1-based grid position of each entry that had one, by entry id; an entry with no
     *                       grid position (an unseeded bump-up slot) is missing
     */
    public record Race(
            long raceId,
            long eventClassId,
            String className,
            int roundNumber,
            String roundType,
            int heatNumber,
            String finalLetter,
            ResultSnapshotDto result,
            Map<Long, Integer> startPositions
    ) {}
}
