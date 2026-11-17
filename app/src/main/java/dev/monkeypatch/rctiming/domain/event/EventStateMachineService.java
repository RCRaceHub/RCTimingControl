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
 * <p>A meeting linked to RaceHub (it has a {@code racehubEventId}) may also go straight to in progress from draft,
 * published or open. RaceHub owns booking, has already closed entries and has released the meeting to timing, so
 * publishing it, opening entries and closing them mean nothing here. The link can arrive late (the first import
 * links an event that was made by hand), so a meeting already published or open the old way must still be able to
 * start. Every other meeting keeps the full path, and nothing else about the table changes for a linked meeting.
 *
 * <p>It only moves the event in memory; the caller saves it.
 */
@Service
public class EventStateMachineService {

    private static final Map<EventStatus, Set<EventStatus>> VALID_TRANSITIONS;

    /** Where a RaceHub-linked meeting may start from: every status before in progress that RaceHub has done for it. */
    private static final Set<EventStatus> SKIPPABLE_BY_RACEHUB =
            EnumSet.of(EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.OPEN);

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

    /**
     * The statuses {@code event} may move to now, in the order of {@link EventStatus}. Used by {@link #transition}
     * and by the event detail, so the admin screen offers exactly the moves the server will accept.
     */
    public Set<EventStatus> allowedMoves(Event event) {
        Set<EventStatus> valid = EnumSet.noneOf(EventStatus.class);
        valid.addAll(VALID_TRANSITIONS.getOrDefault(event.getStatus(), Set.of()));
        if (event.getRacehubEventId() != null && SKIPPABLE_BY_RACEHUB.contains(event.getStatus())) {
            valid.add(EventStatus.IN_PROGRESS);
        }
        return valid;
    }

    /**
     * Sets the event's status to {@code targetStatus} if the move is allowed. The event is changed in memory only.
     *
     * @throws IllegalStateTransitionException if the move is not allowed from the event's current status (HTTP 409)
     */
    public void transition(Event event, EventStatus targetStatus) {
        if (!allowedMoves(event).contains(targetStatus)) {
            throw new IllegalStateTransitionException(
                "Cannot transition from " + event.getStatus() + " to " + targetStatus);
        }
        event.setStatus(targetStatus);
    }
}
