package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/admin/scoring-systems/{id}/clone}: the new system's name. */
public record CloneScoringSystemRequest(@NotBlank @Size(max = 255) String name) {
}
