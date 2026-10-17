package dev.monkeypatch.rctiming.query.racecontrol;

/**
 * One driver on a race's grid ({@link RaceEntriesQuery}).
 *
 * @param racerName the competitor's display name, or "Unknown" when the competitor row is missing
 * @param carNumber the entry's car number for this race, or null when none was recorded
 */
public record RaceEntryDto(long entryId, String racerName, String carNumber) {}
