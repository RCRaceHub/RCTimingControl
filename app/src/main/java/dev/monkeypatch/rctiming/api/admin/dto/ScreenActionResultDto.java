package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.ScreenActionResult;

/** How one screen took a command sent to a group (#708); {@code error} is set when {@code ok} is false. */
public record ScreenActionResultDto(Long id, String screenId, String name, boolean ok, String error) {

    public static ScreenActionResultDto from(ScreenActionResult r) {
        return new ScreenActionResultDto(r.id(), r.screenId(), r.name(), r.ok(), r.error());
    }
}
