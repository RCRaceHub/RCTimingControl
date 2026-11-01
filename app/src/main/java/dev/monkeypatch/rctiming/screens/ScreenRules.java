package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerBanner;
import dev.monkeypatch.rctiming.screens.viewer.ViewerMessage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The checks on what an official types for the screen manager (#708). Every failure is an {@link
 * IllegalArgumentException}, which the API answers with 400 and the message. The viewer agent checks the same things
 * again, so these only spare a round trip and keep nonsense out of the database.
 */
final class ScreenRules {

    /** The shortest dwell a screen accepts unless its config says otherwise. */
    static final int MIN_DURATION = 5;
    static final int MAX_DURATION = 86_400;
    static final int MAX_ITEMS = 100;
    private static final int MAX_URL = 2000;

    private ScreenRules() {
    }

    /** A playlist name, trimmed. */
    static String cleanName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 255) {
            throw new IllegalArgumentException("A playlist needs a name of up to 255 characters");
        }
        return clean;
    }

    /** The playlist's default dwell, in whole seconds. */
    static int checkDefaultDuration(int seconds) {
        if (seconds < MIN_DURATION || seconds > MAX_DURATION) {
            throw new IllegalArgumentException(
                    "The default time per page must be between " + MIN_DURATION + " and " + MAX_DURATION + " seconds");
        }
        return seconds;
    }

    /** The items, each checked and cleaned; at least one and at most {@value #MAX_ITEMS}. */
    static List<PlaylistItem> checkItems(List<PlaylistItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A playlist needs at least one page");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("A playlist can hold at most " + MAX_ITEMS + " pages");
        }
        List<PlaylistItem> clean = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PlaylistItem item = items.get(i);
            String where = "Page " + (i + 1);
            if (item == null) {
                throw new IllegalArgumentException(where + " is empty");
            }
            String url = checkWebUrl(item.url(), where);
            Integer duration = item.duration();
            if (duration != null && (duration < MIN_DURATION || duration > MAX_DURATION)) {
                throw new IllegalArgumentException(where + ": the time must be between " + MIN_DURATION + " and "
                        + MAX_DURATION + " seconds");
            }
            clean.add(new PlaylistItem(url, duration, limit(item.label(), 100, where + " label"),
                    limit(item.id(), 50, where + " id")));
        }
        return clean;
    }

    /** An absolute http or https URL with a host, trimmed. {@code what} names it in the message. */
    static String checkWebUrl(String url, String what) {
        String clean = url == null ? "" : url.strip();
        try {
            URI uri = new URI(clean);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (clean.length() > MAX_URL || uri.getHost() == null || !(scheme.equals("http") || scheme.equals("https"))) {
                throw new IllegalArgumentException(what + ": the address must be a full http:// or https:// URL");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(what + ": the address is not a valid URL");
        }
        return clean;
    }

    /** A screen's host: a name or address, with no scheme, path or port. */
    static String checkHost(String host) {
        String clean = host == null ? "" : host.strip();
        if (!clean.matches("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]+]")) {
            throw new IllegalArgumentException("Enter the screen's host name or IP address, without http:// or a port");
        }
        return clean;
    }

    /** A TCP port. */
    static int checkPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be between 1 and 65535");
        }
        return port;
    }

    /** A temporary page's dwell, or null for until cleared. */
    static Integer checkShowDuration(Integer seconds) {
        if (seconds != null && (seconds < 1 || seconds > MAX_DURATION)) {
            throw new IllegalArgumentException("The time must be between 1 and " + MAX_DURATION + " seconds");
        }
        return seconds;
    }

    /** The longest message title the screens accept. */
    static final int MAX_TITLE = 120;
    /** The longest message text or banner the screens accept. */
    static final int MAX_TEXT = 500;
    private static final List<String> STYLES = List.of("hold", "announce");
    private static final List<String> LEVELS = List.of("info", "warning", "urgent");

    /** A full-screen message, cleaned and ready to send: a title, and style and level from the screens' own lists. */
    static ViewerMessage.Body checkMessage(ScreenMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("A message needs a title");
        }
        String title = message.title() == null ? "" : message.title().strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("A message needs a title");
        }
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("The title can be at most " + MAX_TITLE + " characters");
        }
        String text = blankToNull(message.text());
        if (text != null && text.strip().length() > MAX_TEXT) {
            throw new IllegalArgumentException("The text can be at most " + MAX_TEXT + " characters");
        }
        return new ViewerMessage.Body(title, text == null ? null : text.strip(),
                oneOf(message.style(), STYLES, "style"), oneOf(message.level(), LEVELS, "level"),
                checkShowDuration(message.duration()));
    }

    /** A banner, cleaned and ready to send. */
    static ViewerBanner.Body checkBanner(ScreenBanner banner) {
        String text = banner == null || banner.text() == null ? "" : banner.text().strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A banner needs some text");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("The banner can be at most " + MAX_TEXT + " characters");
        }
        return new ViewerBanner.Body(text, oneOf(banner.level(), LEVELS, "level"), checkShowDuration(banner.duration()));
    }

    private static String oneOf(String value, List<String> allowed, String what) {
        String clean = blankToNull(value);
        if (clean == null) {
            return null;
        }
        String lower = clean.strip().toLowerCase(Locale.ROOT);
        if (!allowed.contains(lower)) {
            throw new IllegalArgumentException("The " + what + " must be one of " + String.join(", ", allowed));
        }
        return lower;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String limit(String value, int max, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException(what + " can be at most " + max + " characters");
        }
        return clean;
    }
}
