package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.OverlayThemeDto;
import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayTheme;
import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayThemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A saved overlay theme for the overlay page opened with {@code ?look=ID}, under the anonymous {@code /api/v1/boards}.
 * It holds the look only (parts shown, layout, colours, separator image address), never timing data. A missing theme is a 404.
 */
@RestController
@RequestMapping("/api/v1/boards/overlay-themes")
public class PublicOverlayThemeController {

    private final OverlayThemeService service;

    public PublicOverlayThemeController(OverlayThemeService service) {
        this.service = service;
    }

    /**
     * The club's default theme, which the plain overlay address opens. {@code GET /default}; 204 with no body when no
     * theme is the default.
     */
    @GetMapping("/default")
    public ResponseEntity<OverlayThemeDto> getDefault() {
        return service.getDefault()
                .map(theme -> ResponseEntity.ok(OverlayThemeDto.from(theme, service.designOf(theme))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** One theme by id. {@code GET /{id}}; 404 if there is none. */
    @GetMapping("/{id}")
    public OverlayThemeDto get(@PathVariable Long id) {
        OverlayTheme theme = service.get(id);
        return OverlayThemeDto.from(theme, service.designOf(theme));
    }
}
