package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayTheme;
import tools.jackson.databind.JsonNode;

/**
 * A saved overlay theme: its id, name and the designer's choices as a JSON object. Answer of {@code GET
 * /api/v1/boards/overlay-themes/{id}} for anyone (the overlay in OBS reads it) and of the admin endpoints under
 * {@code /api/v1/admin/overlay-themes}. It holds looks only, never timing data or personal data.
 */
public record OverlayThemeDto(Long id, String name, JsonNode design) {

    public static OverlayThemeDto from(OverlayTheme theme, JsonNode design) {
        return new OverlayThemeDto(theme.getId(), theme.getName(), design);
    }
}
