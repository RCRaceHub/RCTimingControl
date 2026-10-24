package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.LiveTimingSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The club's live timing thresholds (#505), the body and answer of {@code GET} and {@code PUT
 * /api/v1/admin/club/live-timing-settings}. Times are in milliseconds; see {@link LiveTimingSettings} for what each
 * one does. The service checks the ranges again, and that the gaining-fast limit is not below the trend limit.
 */
public record LiveTimingSettingsDto(
        @Min(0) @Max(10_000) int battleGapMs,
        @Min(0) @Max(2_000) int battleTrendMsPerLap,
        @Min(0) @Max(5_000) int battleFastMsPerLap,
        @Min(0) @Max(10) int battleHoldLaps,
        @Min(0) @Max(60_000) int closeGapMs,
        @Min(0) @Max(10_000) int lateMarginMs) {

    public static LiveTimingSettingsDto from(LiveTimingSettings s) {
        return new LiveTimingSettingsDto(s.battleGapMs(), s.battleTrendMsPerLap(), s.battleFastMsPerLap(),
                s.battleHoldLaps(), s.closeGapMs(), s.lateMarginMs());
    }

    public LiveTimingSettings toSettings() {
        return new LiveTimingSettings(battleGapMs, battleTrendMsPerLap, battleFastMsPerLap, battleHoldLaps,
                closeGapMs, lateMarginMs);
    }
}
