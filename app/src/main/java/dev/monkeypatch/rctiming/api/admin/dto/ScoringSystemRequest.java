package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.scoring.ScoringConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/scoring-systems}: a name and the rules, whose type decides
 * the system's kind and whose numbers are checked when it is saved.
 */
public record ScoringSystemRequest(@NotBlank @Size(max = 255) String name, @NotNull ScoringConfig config) {
}
