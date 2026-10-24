package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeDriversRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a practice driver between entity and row; MapStruct generates the implementation, which
 * {@link PracticeDriverRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PracticeDriverMapper {

    PracticeDriver toEntity(PracticeDriversRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(PracticeDriver d, @MappingTarget PracticeDriversRecord record);
}
