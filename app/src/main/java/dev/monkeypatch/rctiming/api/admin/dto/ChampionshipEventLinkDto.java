package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLink;

/**
 * An event linked to a championship as a numbered round, the answer of
 * {@code POST /api/v1/admin/championships/{id}/events} and part of the championship detail.
 */
public record ChampionshipEventLinkDto(
        Long id,
        Long championshipId,
        Long eventId,
        int roundNumber
) {
    public static ChampionshipEventLinkDto from(ChampionshipEventLink l) {
        return new ChampionshipEventLinkDto(l.getId(), l.getChampionshipId(), l.getEventId(), l.getRoundNumber());
    }
}
