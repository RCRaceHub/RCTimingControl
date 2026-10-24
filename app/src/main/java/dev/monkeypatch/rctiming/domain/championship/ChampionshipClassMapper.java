package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipClassesRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a championship class between entity and row; MapStruct generates the implementation, which
 * {@link ChampionshipClassRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ChampionshipClassMapper {

    ChampionshipClass toEntity(ChampionshipClassesRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ChampionshipClass c, @MappingTarget ChampionshipClassesRecord record);
}
