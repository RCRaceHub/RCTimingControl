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
 * @param minCarsPerHeat        fewest cars worth a heat of their own — a class splits into two or more heats
 *                              (so drivers can marshal each other) once it has this many cars for each
 * @param classFinalsConfigs    per-class overrides for finals configuration
 */
public record RoundGenerationRequest(
        @NotNull Long eventId,
        @Min(0) @Max(10) int practiceRoundsCount,
        @Min(1) @Max(10) int qualifyingRoundsCount,
        @Min(1) @Max(64) int maxCarsPerHeat,
        @Min(1) @Max(64) int minCarsPerHeat,
        @NotNull List<ClassFinalsConfig> classFinalsConfigs
) {

    /** The fewest cars in a heat when the caller gives none. */
    public static final int DEFAULT_MIN_CARS_PER_HEAT = 2;

    public RoundGenerationRequest(Long eventId, int practiceRoundsCount, int qualifyingRoundsCount,
                                  int maxCarsPerHeat, List<ClassFinalsConfig> classFinalsConfigs) {
        this(eventId, practiceRoundsCount, qualifyingRoundsCount, maxCarsPerHeat,
                Math.min(DEFAULT_MIN_CARS_PER_HEAT, maxCarsPerHeat), classFinalsConfigs);
    }

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
