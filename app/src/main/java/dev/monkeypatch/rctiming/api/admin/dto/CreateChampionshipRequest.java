package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/admin/championships}. A null {@code bestXFromYX} or {@code bestXFromYY} means every
 * round counts; the bonus points are 0 or more.
 */
public record CreateChampionshipRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(1) Integer bestXFromYX,          // nullable — championship-wide default
        @Min(1) Integer bestXFromYY,
        @Min(0) int tqBonusPoints,            // CHAMP-07
        @Min(0) int afinalWinnerBonusPoints,  // CHAMP-08
        boolean showOverallTable              // ranks every class together as well
) {}
