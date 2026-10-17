package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

/**
 * {@code rctiming.racehub.*}: where results exports are sent (#27). Sending needs both settings; with either
 * missing, exports wait in the outbox.
 *
 * @param resultsUrl the RaceHub address results exports are POSTed to. It must be https, since the club's
 *                   key goes with every request; plain http is allowed only to this machine, for testing.
 * @param token      the machine credential RaceHub issued for this club, sent as a bearer token
 */
@ConfigurationProperties(prefix = "rctiming.racehub")
public record RaceHubResultsProperties(URI resultsUrl, String token) {

    static final String URL_SETTING = "rctiming.racehub.results-url";
    static final String TOKEN_SETTING = "rctiming.racehub.token";

    /**
     * Normalises both settings: blank counts as unset. Throws at startup for an address that would send the key in
     * the clear to another machine.
     */
    public RaceHubResultsProperties {
        resultsUrl = SecureEndpoint.url(resultsUrl, URL_SETTING, "https", "http");
        token = SecureEndpoint.token(token);
    }

    /** Whether exports are sent: both the address and the key are set. */
    public boolean sendingEnabled() {
        return resultsUrl != null && token != null;
    }

    /** The settings still needed before anything is sent, empty when sending is on. */
    public List<String> missingSettings() {
        return SecureEndpoint.missingSettings(resultsUrl, URL_SETTING, token, TOKEN_SETTING);
    }
}
