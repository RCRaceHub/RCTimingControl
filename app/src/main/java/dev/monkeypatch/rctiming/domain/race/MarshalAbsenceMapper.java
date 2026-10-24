package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAbsencesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a marshal absence between entity and row; MapStruct generates the implementation, which
 * {@link MarshalAbsenceRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MarshalAbsenceMapper {

    MarshalAbsence toEntity(MarshalAbsencesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(MarshalAbsence m, @MappingTarget MarshalAbsencesRecord record);
}
