package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.ScoringSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChampionshipRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(1) Integer bestXFromYX,          // nullable — championship-wide default
        @Min(1) Integer bestXFromYY,
        @NotNull ScoringSource scoringSource, // CHAMP-06
        @Min(0) int tqBonusPoints,            // CHAMP-07
        @Min(0) int afinalWinnerBonusPoints   // CHAMP-08
) {}
