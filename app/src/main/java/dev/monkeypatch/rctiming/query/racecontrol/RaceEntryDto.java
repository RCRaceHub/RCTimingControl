package dev.monkeypatch.rctiming.query.racecontrol;

/**
 * One driver on a race's grid ({@link RaceEntriesQuery}).
 *
 * @param racerName the competitor's display name, or "Unknown" when the competitor row is missing
 * @param carNumber always null for now: the query does not read it yet (ENTRY-03)
 */
public record RaceEntryDto(long entryId, String racerName, String carNumber) {}
