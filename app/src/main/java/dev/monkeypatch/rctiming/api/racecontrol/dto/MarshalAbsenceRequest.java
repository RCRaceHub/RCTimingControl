package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /referee/race/{raceId}/marshal-absent}: the entry that missed its marshal duty and the
 * event it was owed in. {@code notes} is accepted but {@code RefereeController} does not pass it on, so it is not
 * stored.
 */
public record MarshalAbsenceRequest(
        @NotNull Long entryId,
        @NotNull Long eventId,
        String notes
) {}
