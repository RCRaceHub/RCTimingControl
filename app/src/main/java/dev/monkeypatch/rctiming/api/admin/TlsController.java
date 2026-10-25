package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.infrastructure.tls.TlsService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The HTTPS certificates for the Club page (#503): their state, and the two emergency buttons that make them again.
 * Under {@code /api/v1/admin/tls}; ADMIN only. The CA's certificate is offered to everyone at
 * {@code GET /api/v1/about/ca.crt} ({@code AboutController}); its private key is never served.
 */
@RestController
@RequestMapping("/api/v1/admin/tls")
@PreAuthorize("hasRole('ADMIN')")
public class TlsController {

    private final TlsService tls;
    private final AuditService audit;

    public TlsController(TlsService tls, AuditService audit) {
        this.tls = tls;
        this.audit = audit;
    }

    /** Whether HTTPS is on, its addresses, the names and expiry of the certificates. {@code GET}; read only. */
    @GetMapping
    public TlsService.Status status() {
        return tls.status();
    }

    /**
     * Makes the server certificate again, signed by the same CA, so devices need do nothing. Applied to the running
     * HTTPS connector at once. {@code POST}; answers the new status; 409 when HTTPS is switched off.
     */
    @Audited("audit_log")
    @PostMapping("/regenerate-server")
    public TlsService.Status regenerateServer(Authentication auth) {
        requireEnabled();
        tls.regenerateServer();
        audit.entry(CurrentOfficial.actor(auth), "TLS_SERVER_CERTIFICATE_REGENERATED").entity("tls", "server")
                .summary("Made the HTTPS server certificate again").recordStandalone();
        return tls.status();
    }

    /**
     * Makes a new CA and a server certificate from it. Every device that trusted the old CA must install the new one,
     * so the Club page asks for a confirmation first. {@code POST}; answers the new status; 409 when HTTPS is off.
     */
    @Audited("audit_log")
    @PostMapping("/regenerate-ca")
    public TlsService.Status regenerateCa(Authentication auth) {
        requireEnabled();
        tls.regenerateCa();
        audit.entry(CurrentOfficial.actor(auth), "TLS_CA_REGENERATED").entity("tls", "ca")
                .summary("Made the club certificate authority again; devices must install the new one").recordStandalone();
        return tls.status();
    }

    private void requireEnabled() {
        if (!tls.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "HTTPS is switched off. Set rctiming.https.enabled=true and restart.");
        }
    }
}
