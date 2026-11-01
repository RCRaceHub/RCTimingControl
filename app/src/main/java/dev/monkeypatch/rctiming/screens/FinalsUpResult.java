package dev.monkeypatch.rctiming.screens;

import java.util.List;

/**
 * How "Finals are up" went (#716), one list per step so a failure shows which step it was: the playlist push (empty
 * when no playlist was named) and then the announcement. A screen whose playlist did not arrive is listed as failed
 * under {@code message} too, because announcing lists that are not there would mislead.
 */
public record FinalsUpResult(List<ScreenActionResult> playlist, List<ScreenActionResult> message) {
}
