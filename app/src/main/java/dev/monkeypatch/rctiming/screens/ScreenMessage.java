package dev.monkeypatch.rctiming.screens;

/**
 * What an official wants a screen to say across its whole face (#716): used for a one-off message, a saved preset and
 * the messages the event hooks send. Checked by {@link ScreenRules#checkMessage}.
 *
 * @param text     optional second line, at most 500 characters
 * @param style    {@code hold} (a slow pulse, for "please wait") or {@code announce} (news); null means hold
 * @param level    {@code info}, {@code warning} or {@code urgent}; null means info
 * @param duration seconds before the screen drops it by itself, or null to leave it up until cleared
 */
public record ScreenMessage(String title, String text, String style, String level, Integer duration) {
}
