package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipExclusionsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a championship exclusion between entity and row; MapStruct generates the implementation, which
 * {@link ChampionshipExclusionRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ChampionshipExclusionMapper {

    ChampionshipExclusion toEntity(ChampionshipExclusionsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ChampionshipExclusion c, @MappingTarget ChampionshipExclusionsRecord record);
}
