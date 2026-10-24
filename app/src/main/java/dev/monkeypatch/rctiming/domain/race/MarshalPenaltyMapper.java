package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalPenaltiesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a marshal penalty between entity and row; MapStruct generates the implementation, which
 * {@link MarshalPenaltyRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MarshalPenaltyMapper {

    MarshalPenalty toEntity(MarshalPenaltiesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(MarshalPenalty m, @MappingTarget MarshalPenaltiesRecord record);
}
