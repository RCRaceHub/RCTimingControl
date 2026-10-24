package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.TrackLapThresholdsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a track lap threshold between entity and row; MapStruct generates the implementation, which
 * {@link TrackLapThresholdRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TrackLapThresholdMapper {

    @Mapping(target = "racingClassName", ignore = true)
    TrackLapThreshold toEntity(TrackLapThresholdsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(TrackLapThreshold t, @MappingTarget TrackLapThresholdsRecord record);
}
