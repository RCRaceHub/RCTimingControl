package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RaceFormatTemplatesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a race format template between entity and row; MapStruct generates the implementation, which
 * {@link RaceFormatTemplateRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceFormatTemplateMapper {

    RaceFormatTemplate toEntity(RaceFormatTemplatesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RaceFormatTemplate t, @MappingTarget RaceFormatTemplatesRecord record);

    /** Used by MapStruct for the JSON column. */
    default RaceFormatConfig config(String json) {
        return RaceFormatTemplateRepository.CONFIG.convertToEntityAttribute(json);
    }

    default String config(RaceFormatConfig config) {
        return RaceFormatTemplateRepository.CONFIG.convertToDatabaseColumn(config);
    }
}
