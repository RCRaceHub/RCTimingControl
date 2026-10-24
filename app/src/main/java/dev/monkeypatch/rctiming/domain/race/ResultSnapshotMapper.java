package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ResultSnapshotsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a result snapshot between entity and row; MapStruct generates the implementation, which
 * {@link ResultSnapshotRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ResultSnapshotMapper {

    ResultSnapshot toEntity(ResultSnapshotsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ResultSnapshot resultSnapshot, @MappingTarget ResultSnapshotsRecord record);
}
