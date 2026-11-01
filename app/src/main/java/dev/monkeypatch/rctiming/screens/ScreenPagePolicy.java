package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which pages may be put on the screens (#708). With {@code rctiming.screens.restrict-pages} on (the default),
 * a page must be on one of this server's own venue-network hosts ({@link LanAddresses#urls()}: its IP addresses, host
 * name and mDNS name) or on a host in {@code rctiming.screens.allowed-hosts}. That stops a mistyped address, or one
 * pasted from somewhere untrusted, reaching every TV. The port is not compared. Called on request threads when a
 * playlist is saved and when a temporary page is shown; reads the addresses each time, so it follows a laptop moving
 * between networks.
 */
@Component
@EnableConfigurationProperties(ScreensProperties.class)
public class ScreenPagePolicy {

    private final boolean restrict;
    private final List<String> allowedHosts;
    private final LanAddresses lan;

    public ScreenPagePolicy(ScreensProperties properties, LanAddresses lan) {
        this.restrict = properties.restrictPages();
        this.allowedHosts = properties.allowedHosts();
        this.lan = lan;
    }

    /**
     * Checks one page address, already known to be a valid http(s) URL.
     *
     * @param what names the page in the message
     * @throws IllegalArgumentException (400) if its host is not allowed
     */
    public void check(String url, String what) {
        if (!restrict) {
            return;
        }
        check(url, what, allowedHosts());
    }

    private static void check(String url, String what, Set<String> allowed) {
        String host = hostOf(url);
        if (host == null || !allowed.contains(host)) {
            throw new IllegalArgumentException(what + ": screens may only show pages on this server (" + String.join(", ", allowed)
                    + "). To allow another host, add it to rctiming.screens.allowed-hosts");
        }
    }

    /** Checks each item's address. */
    public void checkItems(List<PlaylistItem> items) {
        if (!restrict) {
            return;
        }
        Set<String> allowed = allowedHosts();
        for (int i = 0; i < items.size(); i++) {
            check(items.get(i).url(), "Page " + (i + 1), allowed);
        }
    }

    /** The hosts pages may be on, lower case. */
    Set<String> allowedHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        for (String url : lan.urls()) {
            String host = hostOf(url);
            if (host != null) {
                hosts.add(host);
            }
        }
        for (String configured : allowedHosts) {
            if (configured != null && !configured.isBlank()) {
                // Written as a bare host, host:port, a bare IPv6 address or a URL: read them all as the URL they imply
                String entry = configured.strip();
                String asUrl = entry.contains("://") ? entry
                        : "http://" + (entry.contains(":") && !entry.startsWith("[") && entry.indexOf(':') == entry.lastIndexOf(':')
                        ? entry : entry.contains(":") && !entry.startsWith("[") ? "[" + entry + "]" : entry);
                String host = hostOf(asUrl);
                if (host != null) {
                    hosts.add(host);
                }
            }
        }
        return hosts;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url.strip()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
