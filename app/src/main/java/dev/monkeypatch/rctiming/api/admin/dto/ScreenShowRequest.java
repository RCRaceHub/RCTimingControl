package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/show} (#708): put {@code url} on the screens for {@code seconds}, or
 * until cleared when left out.
 */
public record ScreenShowRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, @NotBlank @Size(max = 2000) String url, Integer seconds,
                                @Size(max = 100) String label) {
}
