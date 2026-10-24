package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventClassesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.Map;

/**
 * Maps an event class between entity and row; MapStruct generates the implementation, which
 * {@link EventClassRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EventClassMapper {

    EventClass toEntity(EventClassesRecord record);

    /**
     * Copies every column except the id, which the repository handles, into the record. The bump count and cars
     * per final columns are left alone, as before.
     */
    @Mapping(target = "bumpCount", ignore = true)
    @Mapping(target = "carsPerFinal", ignore = true)
    @Mapping(target = "id", ignore = true)
    void toRecord(EventClass ec, @MappingTarget EventClassesRecord record);

    /** Used by MapStruct for the JSON columns. */
    default RaceFormatConfig configSnapshot(String json) {
        return EventClassRepository.CONFIG.convertToEntityAttribute(json);
    }

    default String configSnapshot(RaceFormatConfig config) {
        return EventClassRepository.CONFIG.convertToDatabaseColumn(config);
    }

    default Map<String, Object> configOverride(String json) {
        return EventClassRepository.OVERRIDE.convertToEntityAttribute(json);
    }

    default String configOverride(Map<String, Object> override) {
        return EventClassRepository.OVERRIDE.convertToDatabaseColumn(override);
    }
}
