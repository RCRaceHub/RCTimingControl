package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.BoardViewDto;
import dev.monkeypatch.rctiming.domain.boardview.BoardView;
import dev.monkeypatch.rctiming.domain.boardview.BoardViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The layout of a saved board view for the page at {@code /boards/view/{slug}} (#481), under the anonymous {@code
 * /api/v1/boards}. It holds only panel types and sizes, never timing data; each panel reads its own board endpoint.
 * A missing view is a 404.
 */
@RestController
@RequestMapping("/api/v1/boards/views")
public class PublicBoardViewController {

    private final BoardViewService service;

    public PublicBoardViewController(BoardViewService service) {
        this.service = service;
    }

    /** One view by its address name. {@code GET /{slug}}; 404 if there is none. */
    @GetMapping("/{slug}")
    public BoardViewDto get(@PathVariable String slug) {
        BoardView view = service.getBySlug(slug);
        return BoardViewDto.from(view, service.panelsOf(view));
    }
}
