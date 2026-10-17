package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleEntry;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The points one finishing position scores in a championship: the body entries of
 * {@code PUT /api/v1/admin/championships/{id}/points-scale} and the answer. {@code position} is 1 or more,
 * {@code points} 0 or more.
 */
public record PointsScaleEntryDto(
        @NotNull @Min(1) Integer position,
        @NotNull @Min(0) Integer points
) {
    public static PointsScaleEntryDto from(ChampionshipPointsScaleEntry e) {
        return new PointsScaleEntryDto(e.getPosition(), e.getPoints());
    }
}
