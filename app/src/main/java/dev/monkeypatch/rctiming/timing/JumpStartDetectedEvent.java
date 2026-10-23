package dev.monkeypatch.rctiming.timing;

/**
 * A car crossed the line before its turn in a stagger start. Published once per car per run by
 * {@code LapTimingService} on the timing thread, so the jump start is written to the audit log before the referee
 * has decided anything.
 *
 * @param raceId  the race in progress
 * @param entryId the entry that went early
 * @param earlyMs how long before its start the car's first early crossing came, in milliseconds
 */
public record JumpStartDetectedEvent(long raceId, long entryId, long earlyMs) {
}
