package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional
public class ClubProfileService {

    /** The club's details as the setup wizard and the club page edit them. */
    public record ClubDetails(String name, String email, String phone, String websiteUrl, Double latitude,
                              Double longitude, String timezone, String logoType) {}

    private final ClubProfileRepository clubProfileRepository;
    private final GoverningBodyAffiliationRepository affiliationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;

    public ClubProfileService(ClubProfileRepository clubProfileRepository,
                               GoverningBodyAffiliationRepository affiliationRepository,
                               ApplicationEventPublisher eventPublisher,
                               AuditService audit) {
        this.clubProfileRepository = clubProfileRepository;
        this.affiliationRepository = affiliationRepository;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DecoderSettings getDecoderSettings() {
        return clubProfileRepository.findCurrent()
                .map(profile -> new DecoderSettings(
                        profile.getDecoderHost(), profile.getDecoderPort(), profile.getDecoderProtocol()))
                .orElseGet(() -> new DecoderSettings(null, null, null));
    }

    /** The club's profile; empty until the setup wizard saves one. */
    @Transactional(readOnly = true)
    public Optional<ClubProfile> getProfile() {
        return clubProfileRepository.findCurrent();
    }

    /** The announcer settings, or the defaults before a club profile exists. */
    @Transactional(readOnly = true)
    public ClubAudioSettings audioSettings() {
        return clubProfileRepository.findCurrent()
                .map(ClubProfile::getAudioSettings)
                .orElseGet(ClubAudioSettings::defaults);
    }

    /** The club's chosen announcer voice, or empty to use the Piper default. */
    @Transactional(readOnly = true)
    public Optional<String> defaultVoiceId() {
        return clubProfileRepository.findCurrent()
                .map(ClubProfile::getDefaultVoiceId)
                .filter(v -> !v.isBlank());
    }

    /**
     * The profile's id for a write, creating a blank profile first if the setup wizard has not saved one,
     * so the announcer settings or a logo can be stored before the club's details.
     */
    public Long getSingletonProfileId() {
        return clubProfileRepository.findCurrent()
                .map(ClubProfile::getId)
                .orElseGet(() -> {
                    ClubProfile blank = new ClubProfile();
                    blank.setName("");
                    blank.setTimezone("UTC");
                    return clubProfileRepository.save(blank).getId();
                });
    }

    public ClubProfile createOrUpdateProfile(Actor actor, ClubDetails details) {
        try {
            ZoneId.of(details.timezone());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid timezone: " + details.timezone(), e);
        }

        ClubProfile profile = clubProfileRepository.findCurrent().orElseGet(ClubProfile::new);

        boolean isNew = profile.getId() == null;
        Map<String, Object> before = isNew ? null : profileValues(profile);

        profile.setName(details.name());
        profile.setEmail(details.email());
        profile.setPhone(details.phone());
        profile.setWebsiteUrl(details.websiteUrl());
        profile.setLatitude(details.latitude());
        profile.setLongitude(details.longitude());
        profile.setTimezone(details.timezone());
        profile.setLogoType(details.logoType());

        ClubProfile saved = clubProfileRepository.save(profile);
        audit.entry(actor, isNew ? "CLUB_PROFILE_CREATED" : "CLUB_PROFILE_UPDATED")
                .entity("club_profile", saved.getId())
                .summary("Saved the club details for " + saved.getName())
                .before(before).after(profileValues(saved)).record();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<GoverningBodyAffiliation> listAffiliations() {
        return affiliationRepository.findAll();
    }

    public GoverningBodyAffiliation createAffiliation(Actor actor, String code, String displayName,
                                                      boolean membershipRequired) {
        GoverningBodyAffiliation affiliation = new GoverningBodyAffiliation();
        affiliation.setCode(code);
        affiliation.setDisplayName(displayName);
        affiliation.setMembershipRequired(membershipRequired);
        GoverningBodyAffiliation saved = affiliationRepository.save(affiliation);
        audit.entry(actor, "AFFILIATION_CREATED").entity("affiliation", saved.getId())
                .summary("Added the governing body " + saved.getDisplayName())
                .after(affiliationValues(saved)).record();
        return saved;
    }

    public GoverningBodyAffiliation updateAffiliation(Actor actor, Long id, String code, String displayName,
                                                      boolean membershipRequired) {
        GoverningBodyAffiliation affiliation = affiliationRepository.getOrThrow(id);
        Map<String, Object> before = affiliationValues(affiliation);
        affiliation.setCode(code);
        affiliation.setDisplayName(displayName);
        affiliation.setMembershipRequired(membershipRequired);
        GoverningBodyAffiliation saved = affiliationRepository.save(affiliation);
        audit.entry(actor, "AFFILIATION_UPDATED").entity("affiliation", id)
                .summary("Changed the governing body " + saved.getDisplayName())
                .before(before).after(affiliationValues(saved)).record();
        return saved;
    }

    public void deleteAffiliation(Actor actor, Long id) {
        GoverningBodyAffiliation affiliation = affiliationRepository.getOrThrow(id);
        affiliationRepository.deleteById(id);
        audit.entry(actor, "AFFILIATION_DELETED").entity("affiliation", id)
                .summary("Removed the governing body " + affiliation.getDisplayName())
                .before(affiliationValues(affiliation)).record();
    }

    /**
     * Changes the announcer settings with {@code change}, which edits the profile it is given, and records the
     * settings and default voice before and after. Both audio endpoints go through here.
     */
    public ClubProfile changeAudioSettings(Actor actor, Consumer<ClubProfile> change) {
        ClubProfile profile = clubProfileRepository.findById(getSingletonProfileId()).orElseThrow();
        Map<String, Object> before = audioValues(profile);
        change.accept(profile);
        ClubProfile saved = clubProfileRepository.save(profile);
        audit.entry(actor, "AUDIO_SETTINGS_CHANGED").entity("club_profile", saved.getId())
                .summary("Changed the announcer settings")
                .before(before).after(audioValues(saved)).record();
        return saved;
    }

    @Transactional
    public ClubProfile updateDecoderConfig(Actor actor, String host, Integer port, String protocol) {
        ClubProfile profile = clubProfileRepository.findCurrent()
                .orElseThrow(() -> new IllegalStateException("No club profile — complete club step first"));
        Map<String, Object> before = decoderValues(profile);
        profile.setDecoderHost(host);
        profile.setDecoderPort(port);
        profile.setDecoderProtocol(protocol);
        ClubProfile saved = clubProfileRepository.save(profile);
        audit.entry(actor, "DECODER_CONFIG_CHANGED").entity("club_profile", saved.getId())
                .summary(host == null || host.isBlank() ? "Cleared the decoder address"
                        : "Changed the decoder address to " + host + ":" + port + " (" + protocol + ")")
                .before(before).after(decoderValues(saved)).record();
        eventPublisher.publishEvent(new DecoderSettingsChangedEvent(new DecoderSettings(host, port, protocol)));
        return saved;
    }

    private static Map<String, Object> profileValues(ClubProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("email", p.getEmail());
        m.put("phone", p.getPhone());
        m.put("websiteUrl", p.getWebsiteUrl());
        m.put("latitude", p.getLatitude());
        m.put("longitude", p.getLongitude());
        m.put("timezone", p.getTimezone());
        m.put("logoType", p.getLogoType());
        return m;
    }

    private static Map<String, Object> affiliationValues(GoverningBodyAffiliation a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getCode());
        m.put("displayName", a.getDisplayName());
        m.put("membershipRequired", a.isMembershipRequired());
        return m;
    }

    private static Map<String, Object> audioValues(ClubProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settings", p.getAudioSettings());
        m.put("defaultVoiceId", p.getDefaultVoiceId());
        return m;
    }

    private static Map<String, Object> decoderValues(ClubProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("host", p.getDecoderHost());
        m.put("port", p.getDecoderPort());
        m.put("protocol", p.getDecoderProtocol());
        return m;
    }
}
