package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipEventLinksRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a championship event link between entity and row; MapStruct generates the implementation, which
 * {@link ChampionshipEventLinkRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ChampionshipEventLinkMapper {

    ChampionshipEventLink toEntity(ChampionshipEventLinksRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ChampionshipEventLink c, @MappingTarget ChampionshipEventLinksRecord record);
}
