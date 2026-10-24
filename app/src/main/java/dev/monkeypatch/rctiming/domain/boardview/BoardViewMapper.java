package dev.monkeypatch.rctiming.domain.boardview;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.BoardViewsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a board view between entity and row; MapStruct generates the implementation, which {@link BoardViewRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface BoardViewMapper {

    BoardView toEntity(BoardViewsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(BoardView view, @MappingTarget BoardViewsRecord record);
}
