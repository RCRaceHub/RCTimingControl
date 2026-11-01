package dev.monkeypatch.rctiming.screens.viewer;

/**
 * Where to reach one screen's agent: its host (an IP address from discovery, or what an official typed), its port and
 * the bearer token it asks for on changes, or null when it asks for none.
 */
public record ViewerTarget(String host, int port, String token) {

    /** {@code http://host:port/api/v1}, with an IPv6 address in brackets. */
    String baseUrl() {
        String authority = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return "http://" + authority + ":" + port + "/api/v1";
    }
}
