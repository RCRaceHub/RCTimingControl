package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The body of {@code POST /api/v1/admin/screens/push} (#708): a saved playlist and the screens to send it to. */
public record ScreenPushRequest(@NotNull Long playlistId, @NotEmpty List<Long> screenIds) {
}
