package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubFormatLinksRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a format link between entity and row; MapStruct generates the implementation
 * {@link RaceHubFormatLinkRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceHubFormatLinkMapper {

    RaceHubFormatLink toEntity(RacehubFormatLinksRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RaceHubFormatLink link, @MappingTarget RacehubFormatLinksRecord record);
}
