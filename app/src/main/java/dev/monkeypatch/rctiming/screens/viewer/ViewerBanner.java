package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The strip along the bottom of a screen as the screen reports it (agent 0.6.0+). Times are the screen's own clock in
 * Unix seconds; {@code until} is null for a banner that stays until cleared.
 *
 * @param visible false when the screen cannot draw the banner over the page now showing (preload off, or the page
 *                refuses to be framed); the banner is still set, just not seen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerBanner(String text, String level, Double since, Double until, boolean visible) {

    /** The body of {@code PUT /api/v1/banner}; fields left null are left out so the screen applies its defaults. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String text, String level, Integer duration) {
    }

    /** The answer of every banner endpoint: {@code {"banner": {...} | null}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(ViewerBanner banner) {
    }
}
