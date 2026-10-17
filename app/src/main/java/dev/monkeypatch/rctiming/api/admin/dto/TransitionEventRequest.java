package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.event.EventStatus;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/events/{id}/transition}: the state to move the event to. A move the event's
 * state machine does not allow is a 409.
 */
public record TransitionEventRequest(@NotNull EventStatus targetStatus) {}
