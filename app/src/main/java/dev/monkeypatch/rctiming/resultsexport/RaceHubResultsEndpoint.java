package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where RaceHub is, and the club's key for it. The club sets one address, RaceHub's host (such as
 * {@code https://racehub.example}), and every call finds its path from it: results are sent to
 * {@code /api/rctc/v1/results} and ready events are listed at {@code /api/rctc/v1/events?state=ready}, with the
 * class and format lists beside the events. The address saved in the admin panel wins; when none is saved,
 * {@code rctiming.racehub.url} in the settings file is the default, the same way the key works.
 *
 * <p>A club that set the results and events addresses separately before there was one address keeps working: they
 * are used as they were only while no RaceHub address is set, and saving one replaces them. Saving the one address
 * from the admin panel is the only way to set it from there.
 */
@Component
@EnableConfigurationProperties(RaceHubResultsProperties.class)
public class RaceHubResultsEndpoint implements EntryFeedHeaders {

    private static final Logger log = LoggerFactory.getLogger(RaceHubResultsEndpoint.class);

    /** Where results are POSTed, below RaceHub's address. */
    static final String RESULTS_PATH = "/api/rctc/v1/results";
    /** Where live results are POSTed while a race runs, below RaceHub's address. */
    static final String LIVE_PATH = "/api/rctc/v1/live";
    /** Where ready events are listed, below RaceHub's address; the class and format lists sit beside it. */
    static final String EVENTS_PATH = "/api/rctc/v1/events?state=ready";

    /** Where the address in use comes from. */
    public enum AddressSource {
        /** Saved in the admin panel. */
        SAVED,
        /** {@code rctiming.racehub.url} in the settings file. */
        SETTINGS_FILE,
        /** The older separate results and events addresses, used while no RaceHub address is set. */
        SEPARATE,
        /** Nothing is set. */
        NONE
    }

    /**
     * One reading of the settings, so a page or a send pass sees one answer even if the address changes.
     *
     * @param baseUrl         RaceHub's address in use, or null when there is none (or only the older separate ones)
     * @param savedBase       the address saved in the admin panel, or null when the settings file's is in use
     * @param resultsUrl      where results are sent, or null
     * @param eventsUrl       where ready events are listed, or null; the classes and formats are beside it
     * @param source          where the address in use comes from
     * @param sendingEnabled  whether both the address and the key are set
     * @param missingSettings what is still needed before anything is sent
     */
    public record Snapshot(URI baseUrl, String savedBase, URI resultsUrl, URI eventsUrl, AddressSource source,
                           boolean sendingEnabled, List<String> missingSettings) {
    }

    private final RaceHubResultsProperties properties;
    private final ClubProfileService clubProfileService;
    private final RaceHubKeyStore keyStore;

    @Autowired
    public RaceHubResultsEndpoint(RaceHubResultsProperties properties, ClubProfileService clubProfileService,
                                  RaceHubKeyStore keyStore) {
        this.properties = properties;
        this.clubProfileService = clubProfileService;
        this.keyStore = keyStore;
    }

    /** With only the settings file's key. */
    public RaceHubResultsEndpoint(RaceHubResultsProperties properties, ClubProfileService clubProfileService) {
        this(properties, clubProfileService, RaceHubKeyStore.none());
    }

    /**
     * Reads the saved address and the settings file and works out what is in use. A saved address that no longer
     * passes {@link #parseBase} is ignored in favour of the settings file's, so a bad value cannot stop exports.
     */
    public Snapshot snapshot() {
        String saved = clubProfileService.getRaceHubUrl().orElse(null);
        URI base = properties.url();
        AddressSource source = base == null ? AddressSource.NONE : AddressSource.SETTINGS_FILE;
        if (saved != null) {
            try {
                base = parseBase(saved);
                source = AddressSource.SAVED;
            } catch (IllegalArgumentException e) {
                // Only a hand-edited database or a stricter rule later gets here; fall back rather than stop sending
                log.warn("Ignoring the saved RaceHub address: {}", e.getMessage());
                saved = null;
            }
        }
        URI results;
        URI events;
        if (base != null) {
            results = URI.create(base + RESULTS_PATH);
            events = URI.create(base + EVENTS_PATH);
        } else {
            results = separateResultsUrl();
            events = separateEventsUrl();
            source = results != null || events != null ? AddressSource.SEPARATE : AddressSource.NONE;
        }
        List<String> missing = new ArrayList<>();
        if (results == null) {
            missing.add("the RaceHub address");
        }
        if (token() == null) {
            missing.add(RaceHubResultsProperties.TOKEN_SETTING);
        }
        return new Snapshot(base, saved, results, events, source, missing.isEmpty(), missing);
    }

    /** The older results address (saved, else the settings file's), for a club that never set the one address. */
    private URI separateResultsUrl() {
        String saved = clubProfileService.getRaceHubResultsUrl().orElse(null);
        if (saved != null) {
            try {
                return parse(saved);
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring the saved RaceHub results address: {}", e.getMessage());
            }
        }
        return properties.resultsUrl();
    }

    /** The older events address, for a club that never set the one address; one that cannot be used counts as unset. */
    private URI separateEventsUrl() {
        String saved = clubProfileService.getRaceHubEventsUrl().orElse(null);
        if (saved == null) {
            return null;
        }
        try {
            return parse(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring the saved RaceHub events address: {}", e.getMessage());
            return null;
        }
    }

    /** The address to send results to, or null when no RaceHub address is set. */
    public URI resultsUrl() {
        return snapshot().resultsUrl();
    }

    /**
     * The address live results are sent to, or null when no RaceHub address is set. Built only from the one RaceHub
     * address; a club still on the older separate results address gets no live results until it sets the one.
     */
    public URI liveUrl() {
        URI base = snapshot().baseUrl();
        return base == null ? null : URI.create(base + LIVE_PATH);
    }

    /** The address ready events are listed at, or null when no RaceHub address is set. */
    public URI eventsUrl() {
        return snapshot().eventsUrl();
    }

    /**
     * The club's key for RaceHub: the one saved in the admin panel ({@link RaceHubKeyStore}), else the settings
     * file's; null when neither is set. Read on every call, so a rotated key applies without a restart. Never log
     * or return it.
     */
    public String token() {
        return keyStore.saved().orElse(properties.token());
    }

    /** Whether a key saved in the admin panel is in use, so the settings file's is being overridden. */
    public boolean keySaved() {
        return keyStore.saved().isPresent();
    }

    /** Whether the settings file has a key, the one that applies when none is saved. */
    public boolean settingsFileKeySet() {
        return properties.token() != null;
    }

    /** Whether {@code target} is RaceHub's own host: the one RaceHub address in use, or the older separate ones. */
    public boolean isRaceHub(URI target) {
        if (target == null || target.getHost() == null) {
            return false;
        }
        Snapshot now = snapshot();
        return sameHost(target, now.baseUrl()) || sameHost(target, now.resultsUrl()) || sameHost(target, now.eventsUrl());
    }

    /**
     * The Cloudflare Access headers for a request to {@code target}, for a RaceHub behind Cloudflare Access. The
     * service token goes only to RaceHub's own host: the host of the RaceHub address in use. Feed addresses can
     * point anywhere, so any other host gets nothing, and so does a club that has not set {@code rctiming.racehub.access-client-id} and {@code access-client-secret}. The bearer key is still sent
     * as well: Access only lets the request through to RaceHub, which then checks the key.
     */
    public Map<String, String> accessHeaders(URI target) {
        if (!properties.accessConfigured() || target == null || target.getHost() == null) {
            return Map.of();
        }
        return isRaceHub(target)
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

    /** Whether {@link ResultsSender} will send: an address (either source) and the key are both set. */
    public boolean sendingEnabled() {
        return snapshot().sendingEnabled();
    }

    /**
     * Checks one of the older separate addresses: https, or http to this machine only, and no user name or
     * password in it. Throws {@link IllegalArgumentException} if it cannot be used.
     */
    public static URI parse(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            // Not echoed: the address may carry a user name and password
            throw new IllegalArgumentException(
                    "The RaceHub address needs to be a full address, such as https://racehub.example/api/rctc/v1/results");
        }
        SecureEndpoint.url(uri, "The RaceHub address", "https", "http");
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "The RaceHub address can't contain a user name or password; the club's key is sent separately");
        }
        return uri;
    }

    /**
     * Checks the one RaceHub address typed in the admin panel and reduces it to RaceHub's own address: https, or
     * http to this machine only, no user name or password, and nothing from {@code /api/} on, no query and no
     * trailing slash. So pasting an old full address such as {@code https://racehub.example/api/rctc/v1/events} saves
     * {@code https://racehub.example}. Throws {@link IllegalArgumentException} (a 400) if it cannot be used.
     */
    public static URI parseBase(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            // Not echoed: the address may carry a user name and password
            throw new IllegalArgumentException(
                    "The RaceHub address needs to be a full address, such as https://racehub.example");
        }
        SecureEndpoint.url(uri, "The RaceHub address", "https", "http");
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "The RaceHub address can't contain a user name or password; the club's key is sent separately");
        }
        return baseOf(uri);
    }

    /**
     * RaceHub's own address from any address on it: scheme, host, port and the path before {@code /api}, so a RaceHub
     * kept in a sub-folder still works. Query, fragment and user information are dropped.
     */
    public static URI baseOf(URI uri) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        int api = path.indexOf("/api/");
        if (api >= 0) {
            path = path.substring(0, api);
        } else if (path.endsWith("/api")) {
            path = path.substring(0, path.length() - 4);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority().replaceAll("^[^@]*@", "") + path);
    }

    /** The scheme, host and port only, for the audit log. */
    public static String describe(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
    }
}
