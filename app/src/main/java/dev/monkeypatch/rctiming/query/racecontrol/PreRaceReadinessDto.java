package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.List;

/**
 * Response DTO for the pre-race readiness endpoint (CTRL-02, CTRL-07).
 * Powers the two columns in the cockpit right panel between races:
 * the grid call list and the marshal duty list.
 *
 * @param raceId           the target race ID
 * @param raceLabel        human-readable label, e.g. "Qualifying 2 — Modified Touring — Heat 2"
 * @param firstRaceOfEvent true when no race comes before this one in the event; marshalDuty then holds the last
 *                         race of its own round, if that is another race
 * @param gridCall         drivers due on track in grid position order (null positions last)
 * @param marshalDuty      drivers from the immediately preceding race in the event run order
 */
public record PreRaceReadinessDto(
        long raceId,
        String raceLabel,
        boolean firstRaceOfEvent,
        List<GridCallSlotDto> gridCall,
        List<MarshalDutyRowDto> marshalDuty
) {}
