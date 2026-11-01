package dev.monkeypatch.rctiming.screens;

/**
 * How one screen took a command sent to several (#708): a push, a control or a temporary page. A screen that failed
 * does not stop the others; {@code error} then holds the screen's own message, or says it could not be reached.
 */
public record ScreenActionResult(Long id, String screenId, String name, boolean ok, String error) {

    static ScreenActionResult ok(Screen screen) {
        return new ScreenActionResult(screen.getId(), screen.getScreenId(), screen.getName(), true, null);
    }

    static ScreenActionResult failed(Screen screen, String error) {
        return new ScreenActionResult(screen.getId(), screen.getScreenId(), screen.getName(), false, error);
    }
}
