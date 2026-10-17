package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/classes}. The name must be unique; the description is optional.
 */
public record CreateRacingClassRequest(
        @NotBlank @Size(max = 255) String name,
        String description) {
}
