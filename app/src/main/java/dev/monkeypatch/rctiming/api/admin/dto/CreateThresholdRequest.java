package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/tracks/{trackId}/thresholds}. A null {@code racingClassId} sets the track's
 * default, which applies to every class without its own. {@code minLapMs} is the shortest lap that counts, in
 * milliseconds: a passing sooner after the last counted one is ignored. {@code maxLastLapMs}, optional, is the
 * longest to wait for a last lap after a timed race's clock runs out, in milliseconds.
 */
public record CreateThresholdRequest(
        Long racingClassId,
        @NotNull @Min(1) Integer minLapMs,
        @Min(1) Integer maxLastLapMs) {
}
