package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.track.LoopType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of the track endpoints that add or change a decoder loop. {@code loopId} is the loop's identifier as the
 * decoder reports it; {@code isScoringLoop} says whether laps are counted from it.
 */
public record CreateDecoderLoopRequest(
        @NotBlank String loopId,
        @NotBlank String displayName,
        @NotNull LoopType loopType,
        boolean isScoringLoop) {
}
