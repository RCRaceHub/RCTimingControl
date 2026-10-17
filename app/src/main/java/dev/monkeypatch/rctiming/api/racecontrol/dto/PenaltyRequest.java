package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * The body of {@code POST /referee/race/{raceId}/penalty}. {@code value} must be positive: whole laps (at most 100)
 * for a LAP penalty, seconds for a TIME penalty. {@code reason} is required.
 */
public record PenaltyRequest(
        @NotNull Long entryId,
        @NotNull PenaltyType penaltyType,
        @NotNull @Positive BigDecimal value,
        @NotBlank String reason
) {}
