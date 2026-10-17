package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.track.TrackLapThreshold;

/**
 * A lap time threshold on a track. {@code racingClassId} and {@code racingClassName} are null for the track's default,
 * which applies to every class without its own. {@code minLapMs} is the shortest lap that counts, in milliseconds;
 * {@code maxLastLapMs} is null if not set, else the longest to wait for a last lap after a timed race's clock runs
 * out, in milliseconds.
 */
public record TrackLapThresholdDto(
        Long id,
        Long racingClassId,
        String racingClassName,
        int minLapMs,
        Integer maxLastLapMs) {

    public static TrackLapThresholdDto from(TrackLapThreshold t) {
        return new TrackLapThresholdDto(
                t.getId(),
                t.getRacingClassId(),
                t.getRacingClassName(),
                t.getMinLapMs(),
                t.getMaxLastLapMs());
    }
}
