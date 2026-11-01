package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The body of a command for a group of screens that needs nothing else (#708): the screens to send it to. */
public record ScreenIdsRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds) {
}
