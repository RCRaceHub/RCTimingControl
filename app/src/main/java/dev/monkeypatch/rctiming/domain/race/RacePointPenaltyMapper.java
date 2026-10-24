package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacePointPenaltiesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a race point penalty between entity and row; MapStruct generates the implementation, which
 * {@link RacePointPenaltyRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RacePointPenaltyMapper {

    RacePointPenalty toEntity(RacePointPenaltiesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RacePointPenalty p, @MappingTarget RacePointPenaltiesRecord record);
}
