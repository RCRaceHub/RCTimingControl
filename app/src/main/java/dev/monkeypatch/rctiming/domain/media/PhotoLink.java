package dev.monkeypatch.rctiming.domain.media;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A picture an entry import named by address (#699): a driver's headshot or an entry's car photo. The import only
 * records the link; {@link PhotoImportService} fetches it afterwards, outside the import's write transaction.
 *
 * @param kind         what the picture is
 * @param competitorId the competitor, for a {@link Kind#HEADSHOT}; null for a car photo
 * @param entryId      the entry, for a {@link Kind#CAR_PHOTO}; null for a headshot
 * @param url          an {@code https} address, as the file gave it
 */
public record PhotoLink(Kind kind, Long competitorId, Long entryId, String url) {

    /** The longest address accepted. */
    public static final int URL_MAX = 500;

    /** What a link points at. */
    public enum Kind {
        HEADSHOT,
        CAR_PHOTO
    }

    /**
     * Why an address from an import file cannot be used, or null when it can: only {@code https} addresses with a
     * host, no user name or password, and no longer than {@link #URL_MAX}. Whether the host is a public one is checked
     * when the picture is fetched.
     */
    public static String problem(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.length() > URL_MAX) {
            return "is longer than " + URL_MAX + " characters";
        }
        try {
            URI uri = new URI(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return "must be an https address without a user name or password";
            }
            return null;
        } catch (URISyntaxException e) {
            return "is not a valid address";
        }
    }
}
