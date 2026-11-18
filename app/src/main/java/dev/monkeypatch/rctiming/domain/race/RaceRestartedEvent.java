package dev.monkeypatch.rctiming.domain.race;

/**
 * Published by {@link RaceStateMachineService#restart} when a race is reset to {@code PENDING} from any state, inside
 * the restart's transaction. It is separate from {@link RaceStatusChangedEvent}, which a restart does not publish, so
 * that what a race left behind (such as its sector times) can be cleared.
 */
public record RaceRestartedEvent(long raceId) {
}
