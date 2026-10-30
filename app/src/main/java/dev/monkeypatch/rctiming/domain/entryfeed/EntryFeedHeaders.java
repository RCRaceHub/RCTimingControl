package dev.monkeypatch.rctiming.domain.entryfeed;

import java.net.URI;
import java.util.Map;

/**
 * Extra request headers for a feed address, beyond the bearer token. Implemented by whatever knows a host needs
 * them, so the feed client stays free of any one service's settings. An address nobody recognises gets none, so a
 * secret meant for one host is never sent to another.
 */
public interface EntryFeedHeaders {

    /** Headers to send to {@code url}; empty when it needs none. Never null. */
    Map<String, String> headersFor(URI url);
}
