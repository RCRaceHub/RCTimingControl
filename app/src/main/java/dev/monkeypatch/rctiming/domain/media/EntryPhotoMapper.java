package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryPhotosRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an entry's car photo between entity and row; MapStruct generates the implementation, which {@link EntryPhotoRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryPhotoMapper {

    EntryPhoto toEntity(EntryPhotosRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(EntryPhoto photo, @MappingTarget EntryPhotosRecord record);
}
