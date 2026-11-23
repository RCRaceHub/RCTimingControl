package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/admin/racehub/format-links}: a RaceHub race format id and the format template to run it
 * with. A null {@code templateId} removes the link.
 */
public record LinkRaceHubFormatRequest(@NotBlank @Size(max = 150) String formatId, Long templateId) {
}
