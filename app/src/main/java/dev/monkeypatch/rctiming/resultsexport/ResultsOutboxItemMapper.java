package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ResultsOutboxRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a results outbox item between entity and row; MapStruct generates the implementation, which
 * {@link ResultsOutboxRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ResultsOutboxItemMapper {

    ResultsOutboxItem toEntity(ResultsOutboxRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ResultsOutboxItem i, @MappingTarget ResultsOutboxRecord record);
}
