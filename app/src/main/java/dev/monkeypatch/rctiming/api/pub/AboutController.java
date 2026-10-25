package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import dev.monkeypatch.rctiming.infrastructure.network.MdnsAdvertiser;
import dev.monkeypatch.rctiming.infrastructure.tls.TlsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final TlsService tls;

    public AboutController(BuildProperties buildProperties, LanAddresses lanAddresses, MdnsAdvertiser mdns,
                           TlsService tls) {
        this.version = buildProperties.getVersion();
        this.buildTime = buildProperties.getTime();
        this.lanAddresses = lanAddresses;
        this.mdns = mdns;
        this.tls = tls;
    }

    /** {@code GET /api/v1/about}, anonymous: the build's version and time and the venue-network addresses. */
    @GetMapping
    public AboutDto get() {
        return new AboutDto(version, buildTime, lanAddresses.urls(),
                mdns.announcedHost().map(lanAddresses::urlFor).orElse(null),
                tls.enabled() ? lanAddresses.httpsUrls(tls.port()) : List.of(), tls.enabled());
    }

    /**
     * {@code GET /api/v1/about/ca.crt}, anonymous: the club's certificate authority certificate (never its key), which
     * a device installs once to trust the app's HTTPS address (#503). 404 when HTTPS is switched off.
     */
    @GetMapping("/ca.crt")
    public ResponseEntity<String> caCertificate() {
        if (!tls.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rctiming-club-ca.crt\"")
                .body(tls.caPem());
    }

    /**
     * The About page's data. {@code version} and {@code buildTime} come from the build's properties;
     * {@code addresses}: the URLs other devices on the venue network can open (#23);
     * {@code stableAddress}: the mDNS URL such as {@code http://timing.local:8080/} once it is really announced, else
     * null. QR codes use it so printed codes survive an address change, and fall back to {@code addresses};
     * {@code httpsAddresses}: the HTTPS addresses, empty when HTTPS is off; {@code httpsEnabled}: whether the club
     * certificate authority can be downloaded from {@code /api/v1/about/ca.crt} (#503).
     */
    public record AboutDto(String version, Instant buildTime, List<String> addresses, String stableAddress,
                           List<String> httpsAddresses, boolean httpsEnabled) {}
}
