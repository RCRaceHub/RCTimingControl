package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryCarColoursRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an entry's car colours between entity and row; MapStruct generates the implementation, which {@link EntryCarColoursRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryCarColoursMapper {

    EntryCarColours toEntity(EntryCarColoursRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(EntryCarColours colours, @MappingTarget EntryCarColoursRecord record);
}
