package dev.monkeypatch.rctiming.domain.event;

/**
 * Thrown when a state transition is not permitted by a state machine (EVENT-05): an event's by
 * {@link EventStateMachineService}, and a race's by {@code RaceStateMachineService}.
 * Mapped to HTTP 409 by GlobalExceptionHandler.
 */
public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
