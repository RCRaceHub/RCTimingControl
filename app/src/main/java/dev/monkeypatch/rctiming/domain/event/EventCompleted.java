package dev.monkeypatch.rctiming.domain.event;

/**
 * Published by {@link EventService#transition} when an event is marked COMPLETED: the race day is closed, so a backup
 * and a results export start. It is published inside the transition's transaction.
 */
public record EventCompleted(long eventId) {
}
