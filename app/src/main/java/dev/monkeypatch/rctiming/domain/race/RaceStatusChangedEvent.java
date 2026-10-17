package dev.monkeypatch.rctiming.domain.race;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link RaceStateMachineService} whenever a race transitions to a new status, after the race has been
 * saved, so listeners read its new status and times. It is published inside the command's transaction.
 * Listeners can filter on {@code newStatus} to react to specific transitions (e.g. GRID, RUNNING).
 */
public class RaceStatusChangedEvent extends ApplicationEvent {

    private final long raceId;
    private final RaceStatus newStatus;

    public RaceStatusChangedEvent(Object source, long raceId, RaceStatus newStatus) {
        super(source);
        this.raceId = raceId;
        this.newStatus = newStatus;
    }

    public long getRaceId() {
        return raceId;
    }

    public RaceStatus getNewStatus() {
        return newStatus;
    }
}
