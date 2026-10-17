package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.ChampionshipClass;

/**
 * A racing class in a championship, the answer of {@code POST /api/v1/admin/championships/{id}/classes} and part of the
 * championship detail. A null {@code bestXFromYX} or {@code bestXFromYY} means the class uses the championship's value.
 */
public record ChampionshipClassDto(
        Long id,
        Long championshipId,
        Long racingClassId,
        Integer bestXFromYX,
        Integer bestXFromYY
) {
    public static ChampionshipClassDto from(ChampionshipClass c) {
        return new ChampionshipClassDto(
                c.getId(), c.getChampionshipId(), c.getRacingClassId(),
                c.getBestXFromYX(), c.getBestXFromYY());
    }
}
