package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.domain.boardview.BoardPanel;
import dev.monkeypatch.rctiming.domain.boardview.BoardView;

import java.util.List;

/**
 * A saved board view (#481): its address name, display name, number of columns and panels in order. Answer of {@code
 * GET /api/v1/boards/views/{slug}} for anyone, and of the admin endpoints under {@code /api/v1/admin/board-views}.
 */
public record BoardViewDto(Long id, String slug, String name, int columns, List<BoardPanel> panels) {

    public static BoardViewDto from(BoardView view, List<BoardPanel> panels) {
        return new BoardViewDto(view.getId(), view.getSlug(), view.getName(), view.getColumns(), panels);
    }
}
