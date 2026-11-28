package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.net.URI;
import java.util.List;

/**
 * {@code rctiming.racehub.*}: where results exports are sent (#27). Sending needs both settings; with either
 * missing, exports wait in the outbox.
 *
 * @param resultsUrl the full address results exports are POSTed to, from before there was one RaceHub address.
 *                   Used only when {@code url} and the admin panel's address are both unset. It must be https,
 *                   since the club's key goes with every request; plain http is allowed only to this machine, for
 *                   testing.
 * @param token      the machine credential RaceHub issued for this club, sent as a bearer token
 * @param accessClientId     optional: a Cloudflare Access service token's client id, for a RaceHub that sits behind
 *                           Cloudflare Access. Sent, with the secret, as {@code CF-Access-Client-Id} to RaceHub's host
 *                           only (see {@link RaceHubResultsEndpoint#accessHeaders}). Unset for a RaceHub without it.
 * @param accessClientSecret optional: the matching {@code CF-Access-Client-Secret}. Never logged or returned.
 * @param url        the one RaceHub address, its host such as {@code https://racehub.example}: results, the event
 *                   picker, the class and format lists and the entry feeds all find their paths from it. The default
 *                   when the admin panel has none saved; same https rule as {@code resultsUrl}.
 */
@ConfigurationProperties(prefix = "rctiming.racehub")
public record RaceHubResultsProperties(URI resultsUrl, String token, String accessClientId,
                                       String accessClientSecret, URI url) {

    static final String BASE_SETTING = "rctiming.racehub.url";
    static final String URL_SETTING = "rctiming.racehub.results-url";
    static final String TOKEN_SETTING = "rctiming.racehub.token";
    static final String ACCESS_CLIENT_ID_SETTING = "rctiming.racehub.access-client-id";
    static final String ACCESS_CLIENT_SECRET_SETTING = "rctiming.racehub.access-client-secret";

    /**
     * Normalises both settings: blank counts as unset. Throws at startup for an address that would send the key in
     * the clear to another machine, and for a Cloudflare Access id without its secret (or the reverse), which
     * would silently send nothing.
     */
    @ConstructorBinding
    public RaceHubResultsProperties {
        resultsUrl = SecureEndpoint.url(resultsUrl, URL_SETTING, "https", "http");
        URI checked = SecureEndpoint.url(url, BASE_SETTING, "https", "http");
        url = checked == null ? null : RaceHubResultsEndpoint.baseOf(checked);
        token = SecureEndpoint.token(token);
        accessClientId = SecureEndpoint.token(accessClientId);
        accessClientSecret = SecureEndpoint.token(accessClientSecret);
        if ((accessClientId == null) != (accessClientSecret == null)) {
            throw new IllegalArgumentException(ACCESS_CLIENT_ID_SETTING + " and " + ACCESS_CLIENT_SECRET_SETTING
                    + " go together: set both, or neither");
        }
    }

    /** Without the one RaceHub address: the older results address, the key and the Cloudflare Access token. */
    public RaceHubResultsProperties(URI resultsUrl, String token, String accessClientId, String accessClientSecret) {
        this(resultsUrl, token, accessClientId, accessClientSecret, null);
    }

    /** Without Cloudflare Access: the address and the key only. */
    public RaceHubResultsProperties(URI resultsUrl, String token) {
        this(resultsUrl, token, null, null, null);
    }

    /** Whether a Cloudflare Access service token is configured. */
    public boolean accessConfigured() {
        return accessClientId != null;
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
