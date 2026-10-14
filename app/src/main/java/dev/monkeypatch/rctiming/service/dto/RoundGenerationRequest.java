package dev.monkeypatch.rctiming.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Input DTO for the round generator.
 *
 * @param eventId               the event to generate rounds for
 * @param practiceRoundsCount   number of practice rounds (0 = no practice)
 * @param qualifyingRoundsCount number of qualifying rounds (must be >= 1)
 * @param maxCarsPerHeat        maximum cars per heat — controls heat count per class
 * @param classFinalsConfigs    per-class overrides for finals configuration
 */
public record RoundGenerationRequest(
        @NotNull Long eventId,
        @Min(0) @Max(10) int practiceRoundsCount,
        @Min(1) @Max(10) int qualifyingRoundsCount,
        @Min(1) @Max(64) int maxCarsPerHeat,
        @NotNull List<ClassFinalsConfig> classFinalsConfigs
) {

    /**
     * Per-class finals configuration override.
     *
     * @param eventClassId  the event class this config applies to
     * @param finalsCount   number of finals (1=A only, 2=A+B, 3=A+B+C); null = use EventClass column
     */
    public record ClassFinalsConfig(
            Long eventClassId,
            Integer finalsCount
    ) {}
}
