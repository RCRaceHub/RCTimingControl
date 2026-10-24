package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubClassMappingsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a race hub class mapping between entity and row; MapStruct generates the implementation, which
 * {@link RaceHubClassMappingRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceHubClassMappingMapper {

    RaceHubClassMapping toEntity(RacehubClassMappingsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RaceHubClassMapping m, @MappingTarget RacehubClassMappingsRecord record);
}
