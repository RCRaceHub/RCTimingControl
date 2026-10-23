package dev.monkeypatch.rctiming.domain.raceclass;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacingClassesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a racing class between entity and row; MapStruct generates the implementation, which {@link RacingClassRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RacingClassMapper {

    RacingClass toEntity(RacingClassesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RacingClass racingClass, @MappingTarget RacingClassesRecord record);
}
