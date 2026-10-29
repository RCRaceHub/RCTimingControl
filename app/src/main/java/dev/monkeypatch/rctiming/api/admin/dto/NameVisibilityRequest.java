package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.competitor.NameVisibility;
import jakarta.validation.constraints.NotNull;

/** The body of {@code PUT /{id}/name-visibility}: how the name is shown on public screens. */
public record NameVisibilityRequest(@NotNull NameVisibility nameVisibility) {}
