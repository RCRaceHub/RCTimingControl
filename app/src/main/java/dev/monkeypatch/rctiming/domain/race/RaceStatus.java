package dev.monkeypatch.rctiming.domain.race;

/**
 * A race's lifecycle: {@code PENDING}, {@code GRID}, {@code RUNNING}, {@code STOPPED}, {@code FINISHED}. The allowed
 * moves are in {@link RaceStateMachineService}; an abandoned race is a {@code FINISHED} one with an abandoned time.
 */
public enum RaceStatus {
    PENDING, GRID, RUNNING, STOPPED, FINISHED
}
