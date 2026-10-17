package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/formats}: a template's name and its race format config, which is
 * validated when it is saved.
 */
public record CreateRaceFormatTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Valid RaceFormatConfig config) {
}
