package dev.monkeypatch.rctiming.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStateMachineServiceTest {

    private final EventStateMachineService machine = new EventStateMachineService();

    private static Event event(EventStatus status, String racehubEventId) {
        Event e = new Event();
        e.setStatus(status);
        e.setRacehubEventId(racehubEventId);
        return e;
    }

    @Test
    void unlinkedDraftMayOnlyBePublished() {
        assertThat(machine.allowedMoves(event(EventStatus.DRAFT, null))).containsExactly(EventStatus.PUBLISHED);
    }

    @Test
    void unlinkedDraftCannotStartDirectly() {
        Event e = event(EventStatus.DRAFT, null);

        assertThatThrownBy(() -> machine.transition(e, EventStatus.IN_PROGRESS))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void linkedDraftMayBePublishedOrStartedDirectly() {
        assertThat(machine.allowedMoves(event(EventStatus.DRAFT, "E-1")))
                .containsExactlyInAnyOrder(EventStatus.PUBLISHED, EventStatus.IN_PROGRESS);
    }

    @Test
    void linkedDraftStartsDirectly() {
        Event e = event(EventStatus.DRAFT, "E-1");

        machine.transition(e, EventStatus.IN_PROGRESS);

        assertThat(e.getStatus()).isEqualTo(EventStatus.IN_PROGRESS);
    }

    @Test
    void linkedEventKeepsTheNormalTableOnceItLeavesDraft() {
        assertThat(machine.allowedMoves(event(EventStatus.PUBLISHED, "E-1")))
                .containsExactlyInAnyOrder(EventStatus.OPEN, EventStatus.DRAFT);
        assertThat(machine.allowedMoves(event(EventStatus.IN_PROGRESS, "E-1")))
                .containsExactly(EventStatus.COMPLETED);
        assertThat(machine.allowedMoves(event(EventStatus.COMPLETED, "E-1"))).isEmpty();
    }

    @Test
    void linkedEventCannotSkipBackFromInProgress() {
        Event e = event(EventStatus.IN_PROGRESS, "E-1");

        assertThatThrownBy(() -> machine.transition(e, EventStatus.DRAFT))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
