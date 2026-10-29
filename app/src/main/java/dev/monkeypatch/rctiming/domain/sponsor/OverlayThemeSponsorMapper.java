package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.OverlayThemeSponsorsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps an event sponsor between entity and row; MapStruct generates the implementation, which {@link OverlayThemeSponsorRepository} uses. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface OverlayThemeSponsorMapper {

    OverlayThemeSponsor toEntity(OverlayThemeSponsorsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(OverlayThemeSponsor sponsor, @MappingTarget OverlayThemeSponsorsRecord record);
}
