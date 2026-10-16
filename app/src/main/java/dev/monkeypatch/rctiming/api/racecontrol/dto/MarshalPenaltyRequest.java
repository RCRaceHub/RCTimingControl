package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyKind;
import jakarta.validation.constraints.NotNull;

/**
 * @param kind      what the penalty costs the driver: their best qualifying round, or a lap in their first final
 * @param absenceId the recorded absence the penalty is for; when left out, the entry's most recent absence in
 *                  the event is used, or none if the entry has not been recorded absent
 */
public record MarshalPenaltyRequest(
        @NotNull Long entryId,
        @NotNull Long eventId,
        @NotNull MarshalPenaltyKind kind,
        Long absenceId,
        String notes
) {}
