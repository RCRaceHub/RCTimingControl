package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RaceEntriesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a race entry between entity and row; MapStruct generates the implementation, which
 * {@link RaceEntryRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceEntryMapper {

    RaceEntry toEntity(RaceEntriesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RaceEntry raceEntry, @MappingTarget RaceEntriesRecord record);
}
