package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ScoringSystemsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a scoring system between entity and row; MapStruct generates the implementation, which
 * {@link ScoringSystemRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ScoringSystemMapper {

    ScoringSystem toEntity(ScoringSystemsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(ScoringSystem s, @MappingTarget ScoringSystemsRecord record);

    /** Used by MapStruct for the JSON column. */
    default ScoringConfig config(String json) {
        return ScoringSystemRepository.CONFIG.convertToEntityAttribute(json);
    }

    default String config(ScoringConfig config) {
        return ScoringSystemRepository.CONFIG.convertToDatabaseColumn(config);
    }
}
