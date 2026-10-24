package dev.monkeypatch.rctiming.domain.boardview;

import java.util.List;
import java.util.regex.Pattern;

/** What a saved board view must satisfy, kept apart from the service so it can be tested without a database. */
final class BoardViewRules {

    static final int MAX_PANELS = 12;
    static final int MAX_TOP = 30;
    static final int DEFAULT_TOP = 10;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private BoardViewRules() {
    }

    /** The slug, trimmed and lower-cased. @throws IllegalArgumentException (400) unless it is 1 to 40 letters, digits or dashes */
    static String cleanSlug(String slug) {
        String clean = slug == null ? "" : slug.strip().toLowerCase();
        if (!SLUG.matcher(clean).matches()) {
            throw new IllegalArgumentException(
                    "The address name must be 1 to 40 lower case letters, digits or dashes, starting with a letter or digit");
        }
        return clean;
    }

    /** The name without outer spaces. @throws IllegalArgumentException (400) if it is blank or over 255 characters */
    static String cleanName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 255) {
            throw new IllegalArgumentException("The name must be between 1 and 255 characters");
        }
        return clean;
    }

    /**
     * Checks the layout and returns the panels as they are stored: a live top panel always has a number of places
     * (ten when none is given) and the others none.
     *
     * @throws IllegalArgumentException (400) for no panels, too many, a span outside the columns, or a live top
     *                                  panel with a number of places outside 1 to 30
     */
    static List<BoardPanel> validate(int columns, List<BoardPanel> panels) {
        if (columns < 1 || columns > 3) {
            throw new IllegalArgumentException("A view has 1 to 3 columns");
        }
        if (panels == null || panels.isEmpty() || panels.size() > MAX_PANELS) {
            throw new IllegalArgumentException("A view has 1 to " + MAX_PANELS + " panels");
        }
        return panels.stream().map(panel -> {
            if (panel == null || panel.type() == null) {
                throw new IllegalArgumentException("Every panel needs a type");
            }
            if (panel.span() < 1 || panel.span() > columns) {
                throw new IllegalArgumentException("A panel spans 1 to " + columns + " columns");
            }
            if (panel.type() != BoardPanelType.LIVE_TOP) {
                return new BoardPanel(panel.type(), null, panel.span());
            }
            int top = panel.top() == null ? DEFAULT_TOP : panel.top();
            if (top < 1 || top > MAX_TOP) {
                throw new IllegalArgumentException("A live top panel shows 1 to " + MAX_TOP + " places");
            }
            return new BoardPanel(panel.type(), top, panel.span());
        }).toList();
    }
}
