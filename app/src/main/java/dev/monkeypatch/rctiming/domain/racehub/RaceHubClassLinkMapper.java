package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubClassLinksRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a class link between entity and row; MapStruct generates the implementation {@link RaceHubClassLinkRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RaceHubClassLinkMapper {

    RaceHubClassLink toEntity(RacehubClassLinksRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(RaceHubClassLink link, @MappingTarget RacehubClassLinksRecord record);
}
