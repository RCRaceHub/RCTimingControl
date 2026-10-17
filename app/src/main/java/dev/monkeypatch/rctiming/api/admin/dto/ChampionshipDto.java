package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.championship.Championship;
import dev.monkeypatch.rctiming.domain.championship.ScoringSource;

/**
 * A championship's own settings, the answer of the championship list, create and update endpoints under
 * {@code /api/v1/admin/championships}. {@code bestXFromYX} and {@code bestXFromYY} are the best X from Y rounds that
 * count (null when every round counts); {@code tqBonusPoints} and {@code afinalWinnerBonusPoints} are the bonuses for
 * topping qualifying and winning the A final. See {@code docs/scoring.md}.
 */
public record ChampionshipDto(
        Long id,
        String name,
        Integer bestXFromYX,
        Integer bestXFromYY,
        ScoringSource scoringSource,
        int tqBonusPoints,
        int afinalWinnerBonusPoints
) {
    public static ChampionshipDto from(Championship c) {
        return new ChampionshipDto(
                c.getId(), c.getName(),
                c.getBestXFromYX(), c.getBestXFromYY(),
                c.getScoringSource(),
                c.getTqBonusPoints(), c.getAfinalWinnerBonusPoints()
        );
    }
}
