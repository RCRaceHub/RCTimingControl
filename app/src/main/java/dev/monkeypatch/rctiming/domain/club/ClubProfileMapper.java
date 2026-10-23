package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ClubProfilesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Generated at compile time by MapStruct; {@link ClubProfileRepository} calls it to move a club
 * profile between its entity and its row. A column added to the table without a matching entity
 * property, or the reverse, fails the build.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ClubProfileMapper {

    ClubProfile toEntity(ClubProfilesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ClubProfile profile, @MappingTarget ClubProfilesRecord record);

    default ClubAudioSettings audioSettings(String json) {
        return ClubProfileRepository.AUDIO_SETTINGS.convertToEntityAttribute(json);
    }

    default String audioSettings(ClubAudioSettings settings) {
        return ClubProfileRepository.AUDIO_SETTINGS.convertToDatabaseColumn(settings);
    }
}
