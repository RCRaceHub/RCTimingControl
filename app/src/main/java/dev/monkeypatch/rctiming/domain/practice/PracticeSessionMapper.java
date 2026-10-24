package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeSessionsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a practice session between entity and row; MapStruct generates the implementation, which
 * {@link PracticeSessionRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PracticeSessionMapper {

    PracticeSession toEntity(PracticeSessionsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(PracticeSession s, @MappingTarget PracticeSessionsRecord record);
}
