package dev.monkeypatch.rctiming.domain.boardview;

/**
 * One panel of a board view. {@code top} is how many places a {@link BoardPanelType#LIVE_TOP} panel shows (null for the
 * other types); {@code span} is how many of the view's columns it takes.
 */
public record BoardPanel(BoardPanelType type, Integer top, int span) {
}
