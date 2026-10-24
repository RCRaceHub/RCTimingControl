package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import dev.monkeypatch.rctiming.infrastructure.network.MdnsAdvertiser;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * The app's version and the addresses other devices on the venue network can open it at. It is open to
 * anyone, signed in or not, so a spectator's phone or the announcer's laptop can find the boards. Nothing
 * here is secret on a venue network, which is the only place the app runs.
 */
@RestController
@RequestMapping("/api/v1/about")
public class AboutController {

    private final String version;
    private final Instant buildTime;
    private final LanAddresses lanAddresses;
    private final MdnsAdvertiser mdns;

    public AboutController(BuildProperties buildProperties, LanAddresses lanAddresses, MdnsAdvertiser mdns) {
        this.version = buildProperties.getVersion();
        this.buildTime = buildProperties.getTime();
        this.lanAddresses = lanAddresses;
        this.mdns = mdns;
    }

    /** {@code GET /api/v1/about}, anonymous: the build's version and time and the venue-network addresses. */
    @GetMapping
    public AboutDto get() {
        return new AboutDto(version, buildTime, lanAddresses.urls(),
                mdns.announcedHost().map(lanAddresses::urlFor).orElse(null));
    }

    /**
     * The About page's data. {@code version} and {@code buildTime} come from the build's properties;
     * {@code addresses}: the URLs other devices on the venue network can open (#23);
     * {@code stableAddress}: the mDNS URL such as {@code http://timing.local:8080/} once it is really announced, else
     * null. QR codes use it so printed codes survive an address change, and fall back to {@code addresses}.
     */
    public record AboutDto(String version, Instant buildTime, List<String> addresses, String stableAddress) {}
}
