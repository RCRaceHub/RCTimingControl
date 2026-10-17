package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The finals layout to seed, the body of {@code POST /api/v1/admin/events/{id}/seed-finals}. Who goes where comes from
 * the class's stored qualifying results, not the request. {@code carsPerFinal} is how many cars run in each final and
 * {@code bumpCount} how many bump up from a final into the one above it.
 */
public record SeedFinalsRequest(
        @NotNull Long eventClassId,
        @Min(1) int finalsCount,
        @Min(1) int carsPerFinal,
        @Min(0) int bumpCount
) {}
