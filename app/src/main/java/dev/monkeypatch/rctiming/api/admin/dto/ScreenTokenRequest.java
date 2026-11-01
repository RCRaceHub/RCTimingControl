package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The body of {@code PUT /api/v1/admin/screens/token} (#708): one token for the screens listed; blank clears it. */
public record ScreenTokenRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, @Size(max = 255) String token) {
}
