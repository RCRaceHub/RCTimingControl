package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where results exports are sent (#27). The address set in the admin panel wins; when it is empty,
 * {@code rctiming.racehub.results-url} in the settings file is the default. The key is only ever in the
 * settings file.
 */
@Component
@EnableConfigurationProperties(RaceHubResultsProperties.class)
public class RaceHubResultsEndpoint implements EntryFeedHeaders {

    private static final Logger log = LoggerFactory.getLogger(RaceHubResultsEndpoint.class);

    /**
     * One reading of the settings, so a page or a send pass sees one answer even if the address changes.
     *
     * @param resultsUrl      the address in use, or null
     * @param savedUrl        the address set in the admin panel, or null when the settings file's is in use
     * @param sendingEnabled  whether both the address and the key are set
     * @param missingSettings what is still needed before anything is sent
     */
    public record Snapshot(URI resultsUrl, String savedUrl, boolean sendingEnabled, List<String> missingSettings) {
    }

    private final RaceHubResultsProperties properties;
    private final ClubProfileService clubProfileService;

    public RaceHubResultsEndpoint(RaceHubResultsProperties properties, ClubProfileService clubProfileService) {
        this.properties = properties;
        this.clubProfileService = clubProfileService;
    }

    /**
     * Reads the saved address and the settings file and works out what is in use. A saved address that no longer
     * passes {@link #parse} is ignored in favour of the settings file's, so a bad value cannot stop exports.
     */
    public Snapshot snapshot() {
        String saved = clubProfileService.getRaceHubResultsUrl().orElse(null);
        URI url = properties.resultsUrl();
        if (saved != null) {
            try {
                url = parse(saved);
            } catch (IllegalArgumentException e) {
                // Only a hand-edited database or a stricter rule later gets here; fall back rather than stop sending
                log.warn("Ignoring the saved RaceHub address: {}", e.getMessage());
                saved = null;
            }
        }
        List<String> missing = new ArrayList<>();
        if (url == null) {
            missing.add("the RaceHub address");
        }
        if (properties.token() == null) {
            missing.add(RaceHubResultsProperties.TOKEN_SETTING);
        }
        return new Snapshot(url, saved, missing.isEmpty(), missing);
    }

    /** The address to send to, or null when neither the admin panel nor the settings file gives one. */
    public URI resultsUrl() {
        return snapshot().resultsUrl();
    }

    /** The club's key for RaceHub, from the settings file only; null when unset. Never log or return it. */
    public String token() {
        return properties.token();
    }

    /**
     * The Cloudflare Access headers for a request to {@code target}, for a RaceHub behind Cloudflare Access. The
     * service token goes only to RaceHub's own host: the host of the results address in use or of the saved events
     * address. Feed addresses can point anywhere, so any other host gets nothing, and so does a club that has not
     * set {@code rctiming.racehub.access-client-id} and {@code access-client-secret}. The bearer key is still sent
     * as well: Access only lets the request through to RaceHub, which then checks the key.
     */
    public Map<String, String> accessHeaders(URI target) {
        if (!properties.accessConfigured() || target == null || target.getHost() == null) {
            return Map.of();
        }
        boolean raceHub = sameHost(target, snapshot().resultsUrl())
                || sameHost(target, clubProfileService.getRaceHubEventsUrl().map(RaceHubResultsEndpoint::lenient).orElse(null));
        return raceHub
                ? Map.of("CF-Access-Client-Id", properties.accessClientId(),
                        "CF-Access-Client-Secret", properties.accessClientSecret())
                : Map.of();
    }

    @Override
    public Map<String, String> headersFor(URI url) {
        return accessHeaders(url);
    }

    private static boolean sameHost(URI target, URI raceHub) {
        return raceHub != null && raceHub.getHost() != null
                && target.getScheme() != null && target.getScheme().equalsIgnoreCase(raceHub.getScheme())
                && target.getHost().equalsIgnoreCase(raceHub.getHost())
                && target.getPort() == raceHub.getPort();
    }

    /** A saved address read for host matching only; one that cannot be read matches nothing. */
    private static URI lenient(String url) {
        try {
            return URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether {@link ResultsSender} will send: an address (either source) and the key are both set. */
    public boolean sendingEnabled() {
        return snapshot().sendingEnabled();
    }

    /**
     * Checks an address typed in the admin panel: https, or http to this machine only, and no user name or
     * password in it. Throws {@link IllegalArgumentException} (a 400) if it cannot be used.
     */
    public static URI parse(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            // Not echoed: the address may carry a user name and password
            throw new IllegalArgumentException(
                    "The RaceHub address needs to be a full address, such as https://racehub.example/api/results");
        }
        SecureEndpoint.url(uri, "The RaceHub address", "https", "http");
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "The RaceHub address can't contain a user name or password; the club's key is sent separately");
        }
        return uri;
    }

    /** The scheme, host and port only, for the audit log. */
    public static String describe(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
    }
}
