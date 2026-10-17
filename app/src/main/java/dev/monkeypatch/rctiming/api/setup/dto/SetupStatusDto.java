package dev.monkeypatch.rctiming.api.setup.dto;

import dev.monkeypatch.rctiming.service.SetupService;

/**
 * Answer of {@code GET /api/v1/setup/status}. {@code bootstrapped}: any account exists, so the first admin is made.
 * {@code setupComplete}: a club profile exists, so the club step is done.
 */
public record SetupStatusDto(boolean bootstrapped, boolean setupComplete) {

    public static SetupStatusDto from(SetupService.Status s) {
        return new SetupStatusDto(s.bootstrapped(), s.setupComplete());
    }
}
