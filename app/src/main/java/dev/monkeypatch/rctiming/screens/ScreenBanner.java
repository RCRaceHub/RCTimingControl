package dev.monkeypatch.rctiming.screens;

/**
 * A line scrolled along the bottom of a screen (#716). Checked by {@link ScreenRules#checkBanner}.
 *
 * @param text     at most 500 characters
 * @param level    {@code info}, {@code warning} or {@code urgent}; null means info
 * @param duration seconds before the screen drops it by itself, or null to leave it up until cleared
 */
public record ScreenBanner(String text, String level, Integer duration) {
}
