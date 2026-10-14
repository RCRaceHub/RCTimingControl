package dev.monkeypatch.rctiming.query.racecontrol;

/**
 * A row in the marshal duty list — one driver from the previous race, annotated with
 * the number of times they failed to marshal at any race in this event.
 *
 * @param entryId         the entry ID for this driver
 * @param driverName      the competitor's display name (L5)
 * @param carNumber       the car number/label (null if not recorded in the system)
 * @param missedThisEvent count of marshal_absences records for this entry in this event
 */
public record MarshalDutyRowDto(
        long entryId,
        String driverName,
        String carNumber,
        long missedThisEvent
) {}
