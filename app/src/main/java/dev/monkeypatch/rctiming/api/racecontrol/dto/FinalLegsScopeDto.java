package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.event.FinalLegsScope;
import jakarta.validation.constraints.NotNull;

/**
 * The body of the update and the answer of both calls.
 *
 * @param scope {@code A_ONLY} or {@code ALL}
 */
public record FinalLegsScopeDto(@NotNull FinalLegsScope scope) {
}
