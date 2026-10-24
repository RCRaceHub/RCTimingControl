package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.boardview.BoardPanel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/board-views}: the address name, a display name, how many
 * columns the page has and the panels in order. The layout is checked when it is saved.
 */
public record BoardViewRequest(@NotBlank @Size(max = 40) String slug, @NotBlank @Size(max = 255) String name,
                               int columns, @NotNull List<BoardPanel> panels) {
}
