package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DisplayScreensRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps a screen between entity and row; MapStruct generates the implementation, which {@link ScreenRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ScreenMapper {

    Screen toEntity(DisplayScreensRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(Screen screen, @MappingTarget DisplayScreensRecord record);
}
