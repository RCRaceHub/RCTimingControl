package dev.monkeypatch.rctiming.api.setup.dto;

import dev.monkeypatch.rctiming.service.SetupService;

/**
 * Which setup wizard steps are done, answer of {@code GET /api/v1/setup/progress}. {@code staff} is true only once
 * a second official exists beyond the first admin.
 */
public record SetupProgressDto(boolean club, boolean track, boolean format, boolean staff, boolean decoder) {

    public static SetupProgressDto from(SetupService.Progress s) {
        return new SetupProgressDto(s.club(), s.track(), s.format(), s.staff(), s.decoder());
    }
}
