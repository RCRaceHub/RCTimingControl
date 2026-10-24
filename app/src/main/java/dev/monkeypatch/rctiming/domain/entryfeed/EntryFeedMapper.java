package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryFeedsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an entry feed between entity and row; MapStruct generates the implementation, which
 * {@link EntryFeedRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryFeedMapper {

    EntryFeed toEntity(EntryFeedsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(EntryFeed f, @MappingTarget EntryFeedsRecord record);
}
