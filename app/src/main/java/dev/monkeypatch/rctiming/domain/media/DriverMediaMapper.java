package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.CompetitorMediaRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a competitor's media between entity and row; MapStruct generates the implementation, which {@link DriverMediaRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface DriverMediaMapper {

    DriverMedia toEntity(CompetitorMediaRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(DriverMedia media, @MappingTarget CompetitorMediaRecord record);
}
