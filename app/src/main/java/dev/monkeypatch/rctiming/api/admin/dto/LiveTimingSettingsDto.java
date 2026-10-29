package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.LiveTimingSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The club's live timing thresholds (#505), the body and answer of {@code GET} and {@code PUT
 * /api/v1/admin/club/live-timing-settings}. Times are in milliseconds; see {@link LiveTimingSettings} for what each
 * one does; {@code hiddenRowIcons} may be omitted for none. The service checks the ranges again, and that the gaining-fast limit is not below the trend limit.
 */
public record LiveTimingSettingsDto(
        @NotNull @Min(0) @Max(10_000) Integer battleGapMs,
        @NotNull @Min(0) @Max(2_000) Integer battleTrendMsPerLap,
        @NotNull @Min(0) @Max(5_000) Integer battleFastMsPerLap,
        @NotNull @Min(0) @Max(10) Integer battleHoldLaps,
        @NotNull @Min(0) @Max(60_000) Integer closeGapMs,
        @NotNull @Min(0) @Max(10_000) Integer lateMarginMs,
        List<String> hiddenRowIcons) {

    public static LiveTimingSettingsDto from(LiveTimingSettings s) {
        return new LiveTimingSettingsDto(s.battleGapMs(), s.battleTrendMsPerLap(), s.battleFastMsPerLap(),
                s.battleHoldLaps(), s.closeGapMs(), s.lateMarginMs(), s.hiddenRowIcons());
    }

    public LiveTimingSettings toSettings() {
        return new LiveTimingSettings(battleGapMs, battleTrendMsPerLap, battleFastMsPerLap, battleHoldLaps,
                closeGapMs, lateMarginMs, hiddenRowIcons);
    }
}
