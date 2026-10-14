package dev.monkeypatch.rctiming.query.event;

import java.time.LocalDate;
import java.util.List;

public record EventScheduleDto(
        Long id,
        String name,
        LocalDate eventDate,
        List<Long> finishedRaceIds,  // IDs of FINISHED races at this event; empty list if none
        Long championshipId          // null if event is not linked to a championship
) {
}
