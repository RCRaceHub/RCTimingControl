package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService.ClubDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Does the wizard's work from {@link ProvisioningProperties}: the first admin, the club details and the decoder
 * address, in one transaction so a bad value leaves the database untouched.
 *
 * <p>Called once at start-up by {@link ProvisioningRunner}, on the main thread. It refuses to touch a database that
 * already has an account, so the settings file can never overwrite a club that is set up.
 */
@Service
@Transactional
public class ProvisioningService {

    private static final Actor ACTOR = Actor.system("provisioning");

    private final SetupService setupService;
    private final ClubProfileService clubProfileService;

    public ProvisioningService(SetupService setupService, ClubProfileService clubProfileService) {
        this.setupService = setupService;
        this.clubProfileService = clubProfileService;
    }

    /**
     * Applies the settings to a fresh database.
     *
     * @return false, having changed nothing, when an account already exists; true once everything is saved
     * @throws IllegalStateException if the settings are incomplete or inconsistent (a missing password or name,
     *                               a decoder with no club); the message names the setting
     * @throws IllegalArgumentException if a value is invalid (short password, unknown time zone, bad port)
     */
    public boolean apply(ProvisioningProperties settings) {
        if (setupService.getStatus().bootstrapped()) {
            return false;
        }
        ProvisioningProperties.Admin admin = settings.admin();
        require(admin.password(), "rctiming.provisioning.admin.password");
        require(admin.firstName(), "rctiming.provisioning.admin.first-name");
        require(admin.lastName(), "rctiming.provisioning.admin.last-name");
        ProvisioningProperties.Club club = settings.club();
        ProvisioningProperties.Decoder decoder = settings.decoder();
        boolean hasClub = club.name() != null && !club.name().isBlank();
        boolean hasDecoder = decoder.host() != null && !decoder.host().isBlank();
        if (hasDecoder && !hasClub) {
            throw new IllegalStateException(
                    "rctiming.provisioning.decoder.host needs rctiming.provisioning.club.name: the decoder address is kept in the club profile");
        }
        if (hasDecoder && (decoder.port() == null || decoder.port() < 1 || decoder.port() > 65535)) {
            throw new IllegalArgumentException("rctiming.provisioning.decoder.port must be 1 to 65535");
        }
        if (hasDecoder && !Set.of("RC4", "P3").contains(decoder.protocol())) {
            throw new IllegalArgumentException("rctiming.provisioning.decoder.protocol must be RC4 or P3");
        }

        setupService.bootstrap(admin.email().strip(), admin.password(), admin.firstName().strip(), admin.lastName().strip());
        if (hasClub) {
            clubProfileService.createOrUpdateProfile(ACTOR, new ClubDetails(club.name().strip(), blankToNull(club.email()),
                    blankToNull(club.phone()), blankToNull(club.websiteUrl()), null, null, club.timezone()));
        }
        if (hasDecoder) {
            clubProfileService.updateDecoderConfig(ACTOR, decoder.host().strip(), decoder.port(), decoder.protocol());
        }
        return true;
    }

    private static void require(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(setting + " is needed when rctiming.provisioning.admin.email is set");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
