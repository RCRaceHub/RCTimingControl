package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayThemeRepository;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * The sponsors an overlay theme owns: the list officials keep in the Overlay Designer and the overlay's sponsor bar
 * reads when it is opened with {@code ?look=ID}. A theme with at least one sponsor shows them instead of the event's
 * ({@link EventSponsorService}); a theme with none falls back to the event's. Called by
 * {@code AdminOverlayThemeSponsorController} (each change is one write transaction with an audit row) and by the
 * anonymous boards endpoint (reads). Names and logos follow {@link SponsorLogos}; logos are kept in object storage like
 * the event's. A sponsor is a business, so nothing here is personal data. Deleting a theme deletes its sponsors (foreign
 * key cascade); their logo files stay in storage, as for event sponsors.
 */
@Service
@Transactional
public class OverlayThemeSponsorService {

    private final OverlayThemeSponsorRepository repository;
    private final OverlayThemeRepository themeRepository;
    private final ObjectStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public OverlayThemeSponsorService(OverlayThemeSponsorRepository repository, OverlayThemeRepository themeRepository,
                                      ObjectStorageService storage, AuditService audit) {
        this(repository, themeRepository, storage, audit, Clock.systemUTC());
    }

    OverlayThemeSponsorService(OverlayThemeSponsorRepository repository, OverlayThemeRepository themeRepository,
                               ObjectStorageService storage, AuditService audit, Clock clock) {
        this.repository = repository;
        this.themeRepository = themeRepository;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * The theme's sponsors in display order.
     *
     * @throws EntityNotFoundException (404) if there is no such theme
     */
    @ReadTransaction
    public List<OverlayThemeSponsor> list(long themeId) {
        themeRepository.requireExists(themeId);
        return repository.findByThemeId(themeId);
    }

    /**
     * The theme's sponsors for the public overlay: empty, not an error, for a theme that is gone or has none, so the
     * overlay falls back to the event's sponsors.
     */
    @ReadTransaction
    public List<OverlayThemeSponsor> listForOverlay(long themeId) {
        return repository.findByThemeId(themeId);
    }

    /**
     * Adds a sponsor at the end of the theme's list.
     *
     * @throws IllegalArgumentException (400) if the name is blank or too long
     * @throws EntityNotFoundException (404) if there is no such theme
     */
    public OverlayThemeSponsor create(Actor actor, long themeId, String name) {
        themeRepository.requireExists(themeId);
        OverlayThemeSponsor sponsor = new OverlayThemeSponsor();
        sponsor.setThemeId(themeId);
        sponsor.setName(SponsorLogos.cleanName(name));
        sponsor.setSortOrder(repository.findByThemeId(themeId).stream()
                .mapToInt(OverlayThemeSponsor::getSortOrder).max().orElse(-1) + 1);
        OverlayThemeSponsor saved = repository.save(sponsor);
        audit.entry(actor, "OVERLAY_THEME_SPONSOR_ADDED").entity("overlay_theme_sponsor", saved.getId())
                .summary("Added the sponsor " + saved.getName() + " to the overlay theme " + themeName(themeId)).record();
        return saved;
    }

    /** Renames a sponsor. @throws IllegalArgumentException (400) for a bad name; EntityNotFoundException (404) */
    public OverlayThemeSponsor rename(Actor actor, long themeId, long sponsorId, String name) {
        OverlayThemeSponsor sponsor = ofTheme(themeId, sponsorId);
        String before = sponsor.getName();
        sponsor.setName(SponsorLogos.cleanName(name));
        repository.save(sponsor);
        audit.entry(actor, "OVERLAY_THEME_SPONSOR_RENAMED").entity("overlay_theme_sponsor", sponsorId)
                .summary("Renamed the sponsor " + before + " to " + sponsor.getName() + " on the overlay theme "
                        + themeName(themeId)).record();
        return sponsor;
    }

    /**
     * Sets the order of the theme's sponsors: {@code orderedIds} lists them first to last, and any left out keep
     * their relative order after them.
     *
     * @throws IllegalArgumentException (400) if an id is missing or repeated
     * @throws EntityNotFoundException (404) if the theme, or an id's sponsor, is not found
     */
    public List<OverlayThemeSponsor> reorder(Actor actor, long themeId, List<Long> orderedIds) {
        themeRepository.requireExists(themeId);
        List<OverlayThemeSponsor> all = repository.findByThemeId(themeId);
        if (orderedIds.stream().anyMatch(Objects::isNull) || orderedIds.stream().distinct().count() != orderedIds.size()) {
            throw new IllegalArgumentException("The order must list each sponsor once");
        }
        for (Long id : orderedIds) {
            ofTheme(themeId, id);
        }
        int position = 0;
        for (Long id : orderedIds) {
            for (OverlayThemeSponsor sponsor : all) {
                if (sponsor.getId().equals(id)) {
                    sponsor.setSortOrder(position++);
                }
            }
        }
        for (OverlayThemeSponsor sponsor : all) {
            if (!orderedIds.contains(sponsor.getId())) {
                sponsor.setSortOrder(position++);
            }
        }
        repository.saveAll(all);
        audit.entry(actor, "OVERLAY_THEME_SPONSORS_REORDERED").entity("overlay_theme", themeId)
                .summary("Changed the order of the sponsors of the overlay theme " + themeName(themeId)).record();
        return repository.findByThemeId(themeId);
    }

    /** Removes a sponsor. @throws EntityNotFoundException (404) */
    public void delete(Actor actor, long themeId, long sponsorId) {
        OverlayThemeSponsor sponsor = ofTheme(themeId, sponsorId);
        repository.deleteById(sponsorId);
        audit.entry(actor, "OVERLAY_THEME_SPONSOR_REMOVED").entity("overlay_theme_sponsor", sponsorId)
                .summary("Removed the sponsor " + sponsor.getName() + " from the overlay theme " + themeName(themeId)).record();
    }

    /**
     * Stores the uploaded image as the sponsor's logo.
     *
     * @throws IllegalArgumentException (400) for a missing, oversized, unsupported or unreadable file
     * @throws EntityNotFoundException (404) if the sponsor is not on this theme
     */
    public OverlayThemeSponsor uploadLogo(Actor actor, long themeId, long sponsorId, MultipartFile file) {
        // Decoding and scaling can take seconds: do it before the sponsor is loaded
        byte[] normalised = SponsorLogos.normalise(file);
        OverlayThemeSponsor sponsor = ofTheme(themeId, sponsorId);
        sponsor.setLogoUrl(storage.upload(
                "sponsors/theme-" + sponsorId + "-" + clock.millis() + ".png", normalised));
        repository.save(sponsor);
        audit.entry(actor, "OVERLAY_THEME_SPONSOR_LOGO_CHANGED").entity("overlay_theme_sponsor", sponsorId)
                .summary("Replaced the logo of the sponsor " + sponsor.getName() + " on the overlay theme "
                        + themeName(themeId)).record();
        return sponsor;
    }

    private OverlayThemeSponsor ofTheme(long themeId, long sponsorId) {
        OverlayThemeSponsor sponsor = repository.getOrThrow(sponsorId);
        if (!sponsor.getThemeId().equals(themeId)) {
            // Same answer as a missing sponsor, so the id of another theme's sponsor is not confirmed
            throw new EntityNotFoundException("Overlay theme sponsor not found: " + sponsorId);
        }
        return sponsor;
    }

    private String themeName(long themeId) {
        return themeRepository.getOrThrow(themeId).getName();
    }
}
