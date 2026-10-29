package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** The body of {@code POST} and {@code PUT /api/v1/admin/overlay-themes}: a name and the design as a JSON object. */
public record OverlayThemeRequest(@NotBlank @Size(max = 100) String name, @NotNull JsonNode design) {
}
