package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.RaceHubAddressDto;
import dev.monkeypatch.rctiming.api.admin.dto.RaceHubAddressRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubAddressService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one RaceHub address. Under {@code /api/v1/admin/racehub/address}; ADMIN only. Setting it here is enough for
 * results, the "Find RaceHub events" picker, the class and format lists and imports: they all find their paths from it.
 */
@RestController
@RequestMapping("/api/v1/admin/racehub/address")
@AdminOnly
public class RaceHubAddressController {

    private final RaceHubAddressService service;

    public RaceHubAddressController(RaceHubAddressService service) {
        this.service = service;
    }

    /**
     * Saves RaceHub's address; blank clears it so the settings file's applies. {@code PUT}. A 400 if it is not https
     * (or http to this machine) or carries a user name. Audited by host only.
     */
    @Audited("audit_log")
    @PutMapping
    public RaceHubAddressDto set(@RequestBody @Valid RaceHubAddressRequest request) {
        return RaceHubAddressDto.from(service.save(CurrentOfficial.actor(), request.address()));
    }
}
