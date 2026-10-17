package dev.monkeypatch.rctiming.domain.event;

/**
 * The states of a race meeting, in the order it moves through them. {@link EventStateMachineService} holds the allowed
 * moves; {@link #COMPLETED} is final.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    OPEN,
    ENTRIES_CLOSED,
    IN_PROGRESS,
    COMPLETED;
}
