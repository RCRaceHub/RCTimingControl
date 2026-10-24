package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.TracksRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a track between entity and row; MapStruct generates the implementation, which
 * {@link TrackRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TrackMapper {

    @Mapping(target = "decoderLoops", ignore = true)
    @Mapping(target = "lapThresholds", ignore = true)
    Track toEntity(TracksRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(Track t, @MappingTarget TracksRecord record);
}
