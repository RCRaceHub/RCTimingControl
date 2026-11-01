package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/control/{action}} (#708): the screens to send it to, and for {@code
 * goto} the page to jump to, counting from 1 as an official sees it.
 */
public record ScreenControlRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, Integer page) {
}
