package dev.monkeypatch.rctiming.query.event;

import java.time.LocalDate;
import java.util.List;

/**
 * One meeting on the public schedule ({@link EventScheduleQuery}).
 *
 * @param finishedRaceIds ids of the meeting's finished races, empty when none has finished
 * @param championshipId  the championship the meeting counts towards, or null when it is not linked to one
 */
public record EventScheduleDto(
        Long id,
        String name,
        LocalDate eventDate,
        List<Long> finishedRaceIds,  // IDs of FINISHED races at this event; empty list if none
        Long championshipId          // null if event is not linked to a championship
) {
}
