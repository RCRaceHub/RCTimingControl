package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a race between entity and row; MapStruct generates the implementation, which
 * {@link RaceRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceMapper {

    Race toEntity(RacesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(Race race, @MappingTarget RacesRecord record);
}
