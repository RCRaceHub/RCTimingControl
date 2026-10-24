package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAdjustmentsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a marshal adjustment between entity and row; MapStruct generates the implementation, which
 * {@link MarshalAdjustmentRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MarshalAdjustmentMapper {

    MarshalAdjustment toEntity(MarshalAdjustmentsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(MarshalAdjustment m, @MappingTarget MarshalAdjustmentsRecord record);
}
