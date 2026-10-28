package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The body of {@code PUT /{id}/categories}: the tags the driver holds; an empty list clears them. */
public record CategoriesRequest(@NotNull List<@Size(max = 30) String> categories) {}
