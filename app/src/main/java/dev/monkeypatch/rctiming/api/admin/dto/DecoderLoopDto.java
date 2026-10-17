package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.track.DecoderLoop;

/**
 * A decoder loop on a track, the answer of the track loop endpoints and part of {@link TrackDto}. {@code loopType} is
 * the name of a {@code LoopType} ({@code FINISH_LINE}, {@code CHICANE} or {@code OTHER}).
 */
public record DecoderLoopDto(
        Long id,
        String loopId,
        String displayName,
        String loopType,
        boolean isScoringLoop) {

    public static DecoderLoopDto from(DecoderLoop loop) {
        return new DecoderLoopDto(
                loop.getId(),
                loop.getLoopId(),
                loop.getDisplayName(),
                loop.getLoopType() != null ? loop.getLoopType().name() : null,
                loop.isScoringLoop());
    }
}
