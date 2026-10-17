package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.ChampionshipExclusion;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipService;

import java.time.Instant;

/**
 * A driver excluded from one event of a championship, the answer of the exclusion endpoints under
 * {@code /api/v1/admin/championships/{id}/exclusions}. {@code driverId} is the competitor's id and {@code createdBy}
 * the id of the official who recorded it.
 */
public record ChampionshipExclusionDto(
        Long id,
        Long championshipId,
        Long driverId,
        Long eventId,
        String reason,
        Long createdBy,
        /** The official who recorded it, by name; null if that account has since been removed. */
        String createdByName,
        Instant createdAt
) {
    public static ChampionshipExclusionDto from(ChampionshipService.Exclusion exclusion) {
        ChampionshipExclusion x = exclusion.exclusion();
        return new ChampionshipExclusionDto(
                x.getId(), x.getChampionshipId(), x.getDriverId(), x.getEventId(),
                x.getReason(), x.getCreatedBy(), exclusion.createdByName(), x.getCreatedAt());
    }
}
