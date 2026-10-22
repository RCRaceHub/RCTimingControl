package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.scoring.ScoringConfig;
import dev.monkeypatch.rctiming.domain.scoring.ScoringKind;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystem;

/** A scoring system, the answer of the endpoints under {@code /api/v1/admin/scoring-systems}. */
public record ScoringSystemDto(Long id, ScoringKind kind, String name, boolean builtIn, ScoringConfig config) {

    public static ScoringSystemDto from(ScoringSystem s) {
        return new ScoringSystemDto(s.getId(), s.getKind(), s.getName(), s.isBuiltIn(), s.getConfig());
    }
}
