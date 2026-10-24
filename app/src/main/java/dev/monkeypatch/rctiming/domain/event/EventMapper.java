package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an event between entity and row; MapStruct generates the implementation, which
 * {@link EventRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EventMapper {

    Event toEntity(EventsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(Event e, @MappingTarget EventsRecord record);
}
