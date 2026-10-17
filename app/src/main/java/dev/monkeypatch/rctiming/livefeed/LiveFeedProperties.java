package dev.monkeypatch.rctiming.livefeed;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

/**
 * {@code rctiming.livefeed.*}: where the live feed is sent (#28). The feed runs only with both settings.
 *
 * @param relayUrl the relay's WebSocket address. It must be wss, since the club's key goes with the connection;
 *                 plain ws is allowed only to this machine, for testing.
 * @param token    the machine credential the relay issued for this club, sent as a bearer token
 */
@ConfigurationProperties(prefix = "rctiming.livefeed")
public record LiveFeedProperties(URI relayUrl, String token) {

    static final String URL_SETTING = "rctiming.livefeed.relay-url";
    static final String TOKEN_SETTING = "rctiming.livefeed.token";

    /** Normalises both settings: blank counts as unset. Throws at startup for a relay address that would send the key
     * in the clear to another machine. */
    public LiveFeedProperties {
        relayUrl = SecureEndpoint.url(relayUrl, URL_SETTING, "wss", "ws");
        token = SecureEndpoint.token(token);
    }

    /** Whether the feed can run: both the relay address and the key are set. */
    public boolean configured() {
        return relayUrl != null && token != null;
    }

    /** The settings still needed before the feed can run, empty when it can. */
    public List<String> missingSettings() {
        return SecureEndpoint.missingSettings(relayUrl, URL_SETTING, token, TOKEN_SETTING);
    }
}
