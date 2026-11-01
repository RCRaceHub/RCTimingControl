package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DisplayPlaylistsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a playlist between entity and row; MapStruct generates the implementation, which {@link ScreenPlaylistRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ScreenPlaylistMapper {

    ScreenPlaylist toEntity(DisplayPlaylistsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ScreenPlaylist playlist, @MappingTarget DisplayPlaylistsRecord record);
}
