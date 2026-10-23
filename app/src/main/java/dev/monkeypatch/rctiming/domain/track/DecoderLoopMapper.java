package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DecoderLoopsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a decoder loop between entity and row; MapStruct generates the implementation, which {@link DecoderLoopRepository} uses.
 * The loop type is stored as its enum name, which MapStruct converts on its own.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface DecoderLoopMapper {

    @Mapping(target = "scoringLoop", source = "isScoringLoop")
    DecoderLoop toEntity(DecoderLoopsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isScoringLoop", source = "scoringLoop")
    void toRecord(DecoderLoop loop, @MappingTarget DecoderLoopsRecord record);
}
