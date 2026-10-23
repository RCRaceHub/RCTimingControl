package dev.monkeypatch.rctiming.timing.dto;

/**
 * A car that crossed the line before its turn in a stagger start. Sent in the list broadcast on
 * {@code /topic/race/{raceId}/jump-start} and returned by {@code GET /api/v1/race-control/race/{raceId}/jump-starts}.
 *
 * @param entryId    the entry that went early
 * @param driverName the public name live timing shows for the entry
 * @param carNumber  the entry's car number in the race, null if none is recorded
 * @param earlyMs    how long before its start the car's first early crossing came, in milliseconds
 * @param dismissed  whether the referee has taken no action on it; it stays listed so the row keeps its lozenge
 */
public record JumpStartDto(long entryId, String driverName, String carNumber, long earlyMs, boolean dismissed) {
}
