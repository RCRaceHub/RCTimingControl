package dev.monkeypatch.rctiming.query.racecontrol;

import java.util.List;

/**
 * Response DTO for the pre-race readiness endpoint (CTRL-02, CTRL-07).
 * Powers the two columns in the cockpit right panel between races:
 * the grid call list and the marshal duty list.
 *
 * @param raceId           the target race ID
 * @param eventId          the event the race belongs to, which absences and marshal penalties are recorded against
 * @param raceLabel        human-readable label, e.g. "Qualifying 2 — Modified Touring — Heat 2"
 * @param firstRaceOfEvent true when no race comes before this one in the event; marshalDuty then holds the last
 *                         race of its own round, if that is another race
 * @param gridCall         drivers due on track in grid position order (null positions last)
 * @param marshalDuty      drivers from the immediately preceding race in the event run order
 * @param marshalsPending true while the final below this bump-up final is unfinished, so who moves up (and can't
 *                         marshal) is not known and marshalDuty is empty
 * @param startType        how the race starts (STAGGER, GRID or ROLLING); null when not set
 * @param staggerIntervalSeconds the seconds between driver calls in a stagger start
 */
public record PreRaceReadinessDto(
        long raceId,
        long eventId,
        String raceLabel,
        boolean firstRaceOfEvent,
        List<GridCallSlotDto> gridCall,
        List<MarshalDutyRowDto> marshalDuty,
        boolean marshalsPending,
        String startType,
        int staggerIntervalSeconds
) {
    /** The same view with the race's start details, which come from the race and its format, not from SQL. */
    public PreRaceReadinessDto withStart(String startType, int staggerIntervalSeconds) {
        return new PreRaceReadinessDto(raceId, eventId, raceLabel, firstRaceOfEvent, gridCall, marshalDuty,
                marshalsPending, startType, staggerIntervalSeconds);
    }
}
