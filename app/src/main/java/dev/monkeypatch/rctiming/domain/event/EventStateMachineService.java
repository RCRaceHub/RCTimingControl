package dev.monkeypatch.rctiming.domain.event;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The states a race meeting moves through, and the one table that says which moves are allowed:
 * {@code DRAFT → PUBLISHED → OPEN → ENTRIES_CLOSED → IN_PROGRESS → COMPLETED}. Only a published meeting can go
 * back, to draft; from there on it is forward only, and a completed meeting is never reopened, since its
 * results have gone to RaceHub and its championships count them.
 *
 * <p>It only moves the event in memory; the caller saves it.
 */
@Service
public class EventStateMachineService {

    private static final Map<EventStatus, Set<EventStatus>> VALID_TRANSITIONS;

    static {
        Map<EventStatus, Set<EventStatus>> m = new EnumMap<>(EventStatus.class);
        m.put(EventStatus.DRAFT,          EnumSet.of(EventStatus.PUBLISHED));
        m.put(EventStatus.PUBLISHED,      EnumSet.of(EventStatus.OPEN, EventStatus.DRAFT));
        m.put(EventStatus.OPEN,           EnumSet.of(EventStatus.ENTRIES_CLOSED));
        m.put(EventStatus.ENTRIES_CLOSED, EnumSet.of(EventStatus.IN_PROGRESS));
        m.put(EventStatus.IN_PROGRESS,    EnumSet.of(EventStatus.COMPLETED));
        m.put(EventStatus.COMPLETED,      EnumSet.noneOf(EventStatus.class));
        VALID_TRANSITIONS = Map.copyOf(m);
    }

    public void transition(Event event, EventStatus targetStatus) {
        Set<EventStatus> valid = VALID_TRANSITIONS.getOrDefault(event.getStatus(), Set.of());
        if (!valid.contains(targetStatus)) {
            throw new IllegalStateTransitionException(
                "Cannot transition from " + event.getStatus() + " to " + targetStatus);
        }
        event.setStatus(targetStatus);
    }
}
