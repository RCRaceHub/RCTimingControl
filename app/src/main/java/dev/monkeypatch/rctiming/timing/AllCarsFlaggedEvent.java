package dev.monkeypatch.rctiming.timing;

/** Every car that has passed the line has taken the flag after the race's clock ran out, so the race is over. */
public record AllCarsFlaggedEvent(long raceId) {
}
