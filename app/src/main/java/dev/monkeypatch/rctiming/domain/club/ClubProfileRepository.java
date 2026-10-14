package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ClubProfilesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ClubProfiles.CLUB_PROFILES;

/** The club profile. There is at most one; {@link ClubProfileService} keeps it that way. */
@Repository
public class ClubProfileRepository extends JooqRepository<ClubProfile, ClubProfilesRecord> {

    private static final ClubAudioSettingsConverter AUDIO_SETTINGS = new ClubAudioSettingsConverter();

    public ClubProfileRepository(DSLContext dsl) {
        super(dsl, CLUB_PROFILES, CLUB_PROFILES.ID);
    }

    /** The club's profile, or empty before the setup wizard has saved one. */
    public Optional<ClubProfile> findCurrent() {
        return dsl.selectFrom(CLUB_PROFILES).orderBy(CLUB_PROFILES.ID).limit(1).fetchOptional().map(this::toEntity);
    }

    @Override
    protected ClubProfile toEntity(ClubProfilesRecord r) {
        ClubProfile p = new ClubProfile();
        p.setId(r.getId());
        p.setName(r.getName());
        p.setEmail(r.getEmail());
        p.setPhone(r.getPhone());
        p.setWebsiteUrl(r.getWebsiteUrl());
        p.setLatitude(r.getLatitude());
        p.setLongitude(r.getLongitude());
        p.setTimezone(r.getTimezone());
        p.setLogoUrl(r.getLogoUrl());
        p.setAudioSettings(AUDIO_SETTINGS.convertToEntityAttribute(r.getAudioSettings()));
        p.setDefaultVoiceId(r.getDefaultVoiceId());
        p.setDecoderHost(r.getDecoderHost());
        p.setDecoderPort(r.getDecoderPort());
        p.setDecoderProtocol(r.getDecoderProtocol());
        p.setPrivacyNotice(r.getPrivacyNotice());
        p.setCreatedAt(r.getCreatedAt());
        p.setUpdatedAt(r.getUpdatedAt());
        return p;
    }

    @Override
    protected void toRecord(ClubProfile p, ClubProfilesRecord r) {
        r.setName(p.getName());
        r.setEmail(p.getEmail());
        r.setPhone(p.getPhone());
        r.setWebsiteUrl(p.getWebsiteUrl());
        r.setLatitude(p.getLatitude());
        r.setLongitude(p.getLongitude());
        r.setTimezone(p.getTimezone());
        r.setLogoUrl(p.getLogoUrl());
        r.setAudioSettings(AUDIO_SETTINGS.convertToDatabaseColumn(p.getAudioSettings()));
        r.setDefaultVoiceId(p.getDefaultVoiceId());
        r.setDecoderHost(p.getDecoderHost());
        r.setDecoderPort(p.getDecoderPort());
        r.setDecoderProtocol(p.getDecoderProtocol());
        r.setPrivacyNotice(p.getPrivacyNotice());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
    }

    @Override
    protected Long idOf(ClubProfile p) {
        return p.getId();
    }

    @Override
    protected void setId(ClubProfile p, Long id) {
        p.setId(id);
    }
}
