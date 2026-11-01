package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/control/{action}} (#708): the screens to send it to, and for {@code
 * goto} the page to jump to, counting from 1 as an official sees it.
 */
public record ScreenControlRequest(@NotEmpty List<Long> screenIds, Integer page) {
}
