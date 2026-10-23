package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ClubProfilesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ClubProfiles.CLUB_PROFILES;

/** The club profile. There is at most one; {@link ClubProfileService} keeps it that way. */
@Repository
public class ClubProfileRepository extends JooqRepository<ClubProfile, ClubProfilesRecord> {

    static final ClubAudioSettingsConverter AUDIO_SETTINGS = new ClubAudioSettingsConverter();

    private static final ClubProfileMapper MAPPER = Mappers.getMapper(ClubProfileMapper.class);

    public ClubProfileRepository(DSLContext dsl) {
        super(dsl, CLUB_PROFILES, CLUB_PROFILES.ID);
    }

    /** The club's profile, or empty before the setup wizard has saved one. */
    public Optional<ClubProfile> findCurrent() {
        return dsl.selectFrom(CLUB_PROFILES).orderBy(CLUB_PROFILES.ID).limit(1).fetchOptional().map(this::toEntity);
    }

    @Override
    protected ClubProfile toEntity(ClubProfilesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ClubProfile p, ClubProfilesRecord r) {
        MAPPER.toRecord(p, r);
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
