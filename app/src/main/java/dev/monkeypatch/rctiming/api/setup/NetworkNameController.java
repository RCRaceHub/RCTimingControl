package dev.monkeypatch.rctiming.api.setup;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.infrastructure.network.MdnsAdvertiser;
import dev.monkeypatch.rctiming.infrastructure.settings.SettingsFile;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * The laptop's network name for the setup wizard's last step (#604): the name the app announces over mDNS, such as
 * {@code rctiming.local}, so tablets and phones can open the app without knowing the laptop's address. The name is
 * the {@code rctiming.network.mdns-name} setting in {@code application.properties}; it is read when the app starts,
 * so a change needs a restart. Under {@code /api/v1/setup/network-name}; ADMIN only. The wizard cannot rename the
 * laptop itself, only choose the name the app announces.
 */
@RestController
@RequestMapping("/api/v1/setup/network-name")
@PreAuthorize("hasRole('ADMIN')")
public class NetworkNameController {

    static final String SETTING = "rctiming.network.mdns-name";
    /** The name the wizard suggests. */
    static final String SUGGESTED = "rctiming";

    private final SettingsFile settings;
    private final MdnsAdvertiser mdns;
    private final AuditService audit;

    public NetworkNameController(SettingsFile settings, MdnsAdvertiser mdns, AuditService audit) {
        this.settings = settings;
        this.mdns = mdns;
        this.audit = audit;
    }

    /**
     * The name in the settings file (a bare host label, empty when there is none), the name actually being announced
     * (such as {@code rctiming.local}, empty until an interface has taken it), the suggestion, and whether the file
     * and the running app disagree so a restart is needed.
     */
    public record NetworkNameDto(String configuredName, String announcedHost, String suggestedName,
                                 boolean restartNeeded) {
    }

    /** The body of {@code PUT}: a host name such as {@code rctiming} or {@code rctiming.local}; blank switches the name off. */
    public record NetworkNameRequest(@NotNull String name) {
    }

    /** The current name settings. {@code GET}; read only. */
    @GetMapping
    public NetworkNameDto status() {
        return state();
    }

    /**
     * Saves the name to {@code application.properties} for the next start. {@code PUT}; 400 when the name is not a
     * usable host name (letters, digits and hyphens, up to 63 characters, with or without {@code .local}). A blank
     * name switches the announcement off. Audited. Nothing changes until the app restarts.
     */
    @Audited("audit_log")
    @PutMapping
    public NetworkNameDto change(Authentication auth, @RequestBody @Valid NetworkNameRequest request) {
        String label = "";
        if (!request.name().isBlank()) {
            label = MdnsAdvertiser.hostLabel(request.name()).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A network name can use letters, digits and hyphens only"));
        }
        settings.set(SETTING, label);
        audit.entry(CurrentOfficial.actor(auth), "NETWORK_NAME_CHANGED").entity("network", "name")
                .summary(label.isEmpty() ? "Switched the network name off for the next start"
                        : "Set the network name to " + label + ".local for the next start")
                .recordStandalone();
        return state();
    }

    private NetworkNameDto state() {
        String configured = settings.get(SETTING).flatMap(MdnsAdvertiser::hostLabel).orElse("");
        // The file is read at start-up, so a file that differs from the label this run started with needs a restart
        boolean restartNeeded = !configured.equals(mdns.configuredLabel().orElse(""));
        Optional<String> announced = mdns.announcedHost();
        return new NetworkNameDto(configured, announced.orElse(null), SUGGESTED, restartNeeded);
    }
}
