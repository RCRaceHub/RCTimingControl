package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Where results exports are sent (#27). The address set in the admin panel wins; when it is empty,
 * {@code rctiming.racehub.results-url} in the settings file is the default. The key is only ever in the
 * settings file.
 */
@Component
@EnableConfigurationProperties(RaceHubResultsProperties.class)
public class RaceHubResultsEndpoint {

    private final RaceHubResultsProperties properties;
    private final ClubProfileService clubProfileService;

    public RaceHubResultsEndpoint(RaceHubResultsProperties properties, ClubProfileService clubProfileService) {
        this.properties = properties;
        this.clubProfileService = clubProfileService;
    }

    /** The address to send to, or null when neither the admin panel nor the settings file gives one. */
    public URI resultsUrl() {
        return clubProfileService.getRaceHubResultsUrl()
                .map(RaceHubResultsEndpoint::parse)
                .orElse(properties.resultsUrl());
    }

    /** The address set in the admin panel, or null when the settings file's is in use. */
    public String savedUrl() {
        return clubProfileService.getRaceHubResultsUrl().orElse(null);
    }

    public String token() {
        return properties.token();
    }

    public boolean sendingEnabled() {
        return resultsUrl() != null && properties.token() != null;
    }

    /** What is still needed before anything is sent, empty when sending is on. */
    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (resultsUrl() == null) {
            missing.add("the RaceHub address");
        }
        if (properties.token() == null) {
            missing.add(RaceHubResultsProperties.TOKEN_SETTING);
        }
        return missing;
    }

    /** Checks an address typed in the admin panel: https, or http to this machine only. Throws {@link IllegalArgumentException} (a 400) if not. */
    public static URI parse(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            // Not echoed: the address may carry a user name and password
            throw new IllegalArgumentException("The RaceHub address needs to be a full address, such as https://racehub.example/api/results");
        }
        return SecureEndpoint.url(uri, "The RaceHub address", "https", "http");
    }
}
