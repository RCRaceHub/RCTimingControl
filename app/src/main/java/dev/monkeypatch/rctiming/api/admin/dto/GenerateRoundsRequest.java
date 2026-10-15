package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateRoundsRequest(
        @Min(0) @Max(10) int practiceRoundsCount,
        @Min(1) @Max(10) int qualifyingRoundsCount,
        @Min(1) @Max(64) int maxCarsPerHeat,
        @Min(1) @Max(64) Integer minCarsPerHeat,
        @NotNull List<@NotNull @Valid ClassFinalsConfigDto> classFinalsConfigs
) {
    /**
     * How many finals a class has; null keeps the class's own setting. Finals are lettered from A, so there are at
     * most 26. How many cars run in each, and how many bump up, are given when the finals are seeded.
     */
    public record ClassFinalsConfigDto(
            @NotNull Long eventClassId,
            @Min(1) @Max(26) Integer finalsCount
    ) {}

    public RoundGenerationRequest toServiceRequest(long eventId) {
        int min = minCarsPerHeat != null ? minCarsPerHeat : RoundGenerationRequest.DEFAULT_MIN_CARS_PER_HEAT;
        return new RoundGenerationRequest(eventId, practiceRoundsCount, qualifyingRoundsCount, maxCarsPerHeat, min,
                classFinalsConfigs.stream()
                        .map(c -> new RoundGenerationRequest.ClassFinalsConfig(
                                c.eventClassId(), c.finalsCount()))
                        .toList());
    }
}
