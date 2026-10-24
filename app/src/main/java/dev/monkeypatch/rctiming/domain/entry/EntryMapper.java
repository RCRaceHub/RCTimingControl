package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntriesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an entry between entity and row; MapStruct generates the implementation, which
 * {@link EntryRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryMapper {

    @Mapping(target = "transponderNumberSnapshot", source = "transponderNumber")
    Entry toEntity(EntriesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "transponderNumber", source = "transponderNumberSnapshot")
    @Mapping(target = "id", ignore = true)
    void toRecord(Entry e, @MappingTarget EntriesRecord record);
}
