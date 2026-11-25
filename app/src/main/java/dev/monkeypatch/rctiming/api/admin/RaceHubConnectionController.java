package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.RaceHubConnectionDto;
import dev.monkeypatch.rctiming.api.admin.dto.RaceHubKeyRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ResultsUrlRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubKeyService;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The club's RaceHub address and key under {@code /api/v1/admin/racehub/connection}. ADMIN only; changes are audited.
 * The connection is core, not part of the results export module: the RaceHub imports of classes, events and formats
 * use it too, so it works whether or not results are sent.
 */
@RestController
@RequestMapping("/api/v1/admin/racehub/connection")
@AdminOnly
public class RaceHubConnectionController {

    private final RaceHubResultsEndpoint endpoint;
    private final ClubProfileService clubProfileService;
    private final RaceHubKeyService keyService;

    public RaceHubConnectionController(RaceHubResultsEndpoint endpoint, ClubProfileService clubProfileService,
                                       RaceHubKeyService keyService) {
        this.endpoint = endpoint;
        this.clubProfileService = clubProfileService;
        this.keyService = keyService;
    }

    /** The address and key as they stand; the key itself is never returned. */
    @GetMapping
    public RaceHubConnectionDto get() {
        var settings = endpoint.snapshot();
        return new RaceHubConnectionDto(settings.sendingEnabled(),
                settings.resultsUrl() == null ? null : settings.resultsUrl().toString(),
                settings.savedUrl(),
                settings.missingSettings(),
                endpoint.keySaved() ? "SAVED" : endpoint.settingsFileKeySet() ? "SETTINGS_FILE" : "NONE");
    }

    /** Saves the RaceHub results address, or clears it when blank. {@code PUT /address}; 400 for a bad address. */
    @Audited("audit_log")
    @PutMapping("/address")
    public RaceHubConnectionDto setAddress(@RequestBody @Valid ResultsUrlRequest request) {
        if (request.resultsUrl() != null && !request.resultsUrl().isBlank()) {
            RaceHubResultsEndpoint.parse(request.resultsUrl());
        }
        clubProfileService.updateRaceHubResultsUrl(CurrentOfficial.actor(), request.resultsUrl());
        return get();
    }

    /**
     * Saves a new RaceHub key, used by the next request with no restart, and gives it to the entry feeds that point
     * at RaceHub. {@code PUT /key}; the key is never returned. A 400 for a key with spaces. Audited without the key.
     */
    @Audited("audit_log")
    @PutMapping("/key")
    public RaceHubConnectionDto setKey(@RequestBody @Valid RaceHubKeyRequest request) {
        keyService.save(CurrentOfficial.actor(), request.key());
        return get();
    }

    /** Forgets the saved key so the settings file's applies again. {@code DELETE /key}. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/key")
    public RaceHubConnectionDto clearKey() {
        keyService.clear(CurrentOfficial.actor());
        return get();
    }
}
