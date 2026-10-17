package dev.monkeypatch.rctiming.domain.entry;

/**
 * Where an entry stands. {@link #WITHDRAWN} entries are kept for the record but no longer race, hold a class place or
 * count as a transponder user. A walk-in is {@link #CONFIRMED} when it is added.
 */
public enum EntryStatus {
    PENDING,
    CONFIRMED,
    WITHDRAWN;
}
