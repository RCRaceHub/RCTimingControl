package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/classes}. The name must be unique; the description is
 * optional. The colour is optional too: {@code #rrggbb}, or blank for none. A {@code PUT} replaces the whole class,
 * so leaving the colour out clears it.
 */
public record CreateRacingClassRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Pattern(regexp = "^(#[0-9a-fA-F]{6})?$", message = "colour must be #rrggbb") String colour) {
}
