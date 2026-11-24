package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.event.PracticeRanking;
import jakarta.validation.constraints.NotNull;

/**
 * The body of the update and the answer of both calls.
 *
 * @param ranking {@code FASTEST}, {@code BEST_2} or {@code BEST_3}
 */
public record PracticeRankingDto(@NotNull PracticeRanking ranking) {
}
