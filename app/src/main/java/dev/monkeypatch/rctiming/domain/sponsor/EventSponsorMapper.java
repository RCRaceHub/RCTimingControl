package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventSponsorsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an event sponsor between entity and row; MapStruct generates the implementation, which {@link EventSponsorRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EventSponsorMapper {

    EventSponsor toEntity(EventSponsorsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(EventSponsor sponsor, @MappingTarget EventSponsorsRecord record);
}
