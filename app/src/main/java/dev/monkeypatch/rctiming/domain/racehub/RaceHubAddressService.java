package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import org.springframework.stereotype.Service;

/**
 * Setting RaceHub's address once, in the admin panel. The address is RaceHub's host; results, the event picker, the
 * class and format lists and the entry feeds all find their paths from it (see {@link RaceHubResultsEndpoint}), and
 * it takes effect on the next request with no restart. It overrides {@code rctiming.racehub.url} in the settings
 * file, the way a saved key overrides the file's key.
 *
 * <p>Called from {@code RaceHubAddressController} on a request thread, ADMIN only. Saving replaces the older
 * separate results and events addresses of a club that set those one by one, because the one address now wins over
 * them.
 */
@Service
public class RaceHubAddressService {

    private final ClubProfileService clubProfileService;
    private final RaceHubResultsEndpoint endpoint;

    public RaceHubAddressService(ClubProfileService clubProfileService, RaceHubResultsEndpoint endpoint) {
        this.clubProfileService = clubProfileService;
        this.endpoint = endpoint;
    }

    /**
     * Saves RaceHub's address, reduced to its host (anything from {@code /api/} on is dropped, so an old full address
     * can be pasted); blank clears it so the settings file's applies. Audited by host only.
     *
     * @throws IllegalArgumentException if it is not https (or http to this machine) or carries a user name (HTTP 400)
     */
    public RaceHubResultsEndpoint.Snapshot save(Actor actor, String address) {
        String cleaned = address == null || address.isBlank() ? null : RaceHubResultsEndpoint.parseBase(address).toString();
        clubProfileService.updateRaceHubUrl(actor, cleaned);
        return endpoint.snapshot();
    }
}
