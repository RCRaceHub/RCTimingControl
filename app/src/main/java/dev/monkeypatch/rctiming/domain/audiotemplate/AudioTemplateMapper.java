package dev.monkeypatch.rctiming.domain.audiotemplate;

import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettingsConverter;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.AudioTemplatesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an audio template between entity and row; MapStruct generates the implementation, which {@link AudioTemplateRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AudioTemplateMapper {

    ClubAudioSettingsConverter SETTINGS = new ClubAudioSettingsConverter();

    AudioTemplate toEntity(AudioTemplatesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(AudioTemplate template, @MappingTarget AudioTemplatesRecord record);

    default ClubAudioSettings settings(String json) {
        return SETTINGS.convertToEntityAttribute(json);
    }

    default String settings(ClubAudioSettings settings) {
        return SETTINGS.convertToDatabaseColumn(settings);
    }
}
