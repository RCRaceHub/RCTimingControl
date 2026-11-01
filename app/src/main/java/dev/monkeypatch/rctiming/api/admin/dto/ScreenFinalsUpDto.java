package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.FinalsUpResult;

import java.util.List;

/** How "Finals are up" went (#716), one list per step: {@code playlist} (empty when none was named) and {@code message}. */
public record ScreenFinalsUpDto(List<ScreenActionResultDto> playlist, List<ScreenActionResultDto> message) {

    public static ScreenFinalsUpDto from(FinalsUpResult r) {
        return new ScreenFinalsUpDto(r.playlist().stream().map(ScreenActionResultDto::from).toList(),
                r.message().stream().map(ScreenActionResultDto::from).toList());
    }
}
