package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.TlsSettingRequest;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.infrastructure.settings.SettingsFile;
import dev.monkeypatch.rctiming.infrastructure.tls.TlsService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * The HTTPS certificates for the Club page (#503): their state, and the two emergency buttons that make them again.
 * Also the switch that turns HTTPS on or off, which is written to {@code application.properties} and takes effect at the
 * next restart (#510). Under {@code /api/v1/admin/tls}; ADMIN only. The CA's certificate is offered to everyone at
 * {@code GET /api/v1/about/ca.crt} ({@code AboutController}); its private key is never served.
 */
@RestController
@RequestMapping("/api/v1/admin/tls")
@AdminOnly
public class TlsController {

    private final TlsService tls;
    private final AuditService audit;
    private final SettingsFile settings;
    private final int httpPort;

    public TlsController(TlsService tls, AuditService audit, SettingsFile settings,
            ObjectProvider<ServerProperties> server) {
        ServerProperties properties = server.getIfAvailable();
        this.httpPort = properties == null || properties.getPort() == null ? 8080 : properties.getPort();
        this.tls = tls;
        this.audit = audit;
        this.settings = settings;
    }

    /**
     * The state of HTTPS: what is running, and what the settings file says for the next start. {@code switchedOn} and
     * {@code configuredPort} are the file's values; {@code restartNeeded} is true when they differ from what is running.
     */
    public record TlsStateDto(boolean enabled, int port, List<String> urls, List<String> names,
                              Instant caExpires, Instant serverExpires, String caFingerprint,
                              boolean switchedOn, int configuredPort, boolean restartNeeded) {}

    /** Whether HTTPS is on, its addresses, the names and expiry of the certificates. {@code GET}; read only. */
    @GetMapping
    public TlsStateDto status() {
        return state();
    }

    /**
     * Switches HTTPS on or off for the next start by writing {@code rctiming.https.enabled} (and the port, when given)
     * to {@code application.properties} in the data folder. {@code PUT /setting}; 400 for a port outside 1 to 65535.
     * Nothing changes until the app restarts, which the answer's {@code restartNeeded} says. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/setting")
    public TlsStateDto change(@RequestBody @Valid TlsSettingRequest request) {
        if (request.port() != null && request.port() == httpPort) {
            throw new IllegalArgumentException(
                    "Port " + httpPort + " is already used by plain HTTP; choose another for HTTPS");
        }
        settings.set("rctiming.https.enabled", String.valueOf(request.enabled()));
        if (request.port() != null) {
            settings.set("rctiming.https.port", String.valueOf(request.port()));
        }
        audit.entry(CurrentOfficial.actor(), "TLS_SETTING_CHANGED").entity("tls", "setting")
                .summary((request.enabled() ? "Switched HTTPS on" : "Switched HTTPS off")
                        + " for the next start" + (request.port() == null ? "" : " on port " + request.port()))
                .recordStandalone();
        return state();
    }

    private TlsStateDto state() {
        TlsService.Status running = tls.status();
        boolean switchedOn = settings.get("rctiming.https.enabled").map(Boolean::parseBoolean).orElse(false);
        int configuredPort = settings.get("rctiming.https.port").map(TlsController::parsePort).orElse(running.port());
        return new TlsStateDto(running.enabled(), running.port(), running.urls(), running.names(),
                running.caExpires(), running.serverExpires(), running.caFingerprint(), switchedOn, configuredPort,
                switchedOn != running.enabled() || (switchedOn && configuredPort != running.port()));
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 8443;
        }
    }

    /**
     * Makes the server certificate again, signed by the same CA, so devices need do nothing. Applied to the running
     * HTTPS connector at once. {@code POST}; answers the new status; 409 when HTTPS is switched off.
     */
    @Audited("audit_log")
    @PostMapping("/regenerate-server")
    public TlsStateDto regenerateServer() {
        requireEnabled();
        tls.regenerateServer();
        audit.entry(CurrentOfficial.actor(), "TLS_SERVER_CERTIFICATE_REGENERATED").entity("tls", "server")
                .summary("Made the HTTPS server certificate again").recordStandalone();
        return state();
    }

    /**
     * Makes a new CA and a server certificate from it. Every device that trusted the old CA must install the new one,
     * so the Club page asks for a confirmation first. {@code POST}; answers the new status; 409 when HTTPS is off.
     */
    @Audited("audit_log")
    @PostMapping("/regenerate-ca")
    public TlsStateDto regenerateCa() {
        requireEnabled();
        tls.regenerateCa();
        audit.entry(CurrentOfficial.actor(), "TLS_CA_REGENERATED").entity("tls", "ca")
                .summary("Made the club certificate authority again; devices must install the new one").recordStandalone();
        return state();
    }

    private void requireEnabled() {
        if (!tls.active()) {
            throw new StateConflictException(
                    "HTTPS is switched off. Set rctiming.https.enabled=true and restart.");
        }
    }
}
