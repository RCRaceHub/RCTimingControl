package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.GoverningBodyAffiliationsRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an affiliation between entity and row; MapStruct generates the implementation, which
 * {@link GoverningBodyAffiliationRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface GoverningBodyAffiliationMapper {

    GoverningBodyAffiliation toEntity(GoverningBodyAffiliationsRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(GoverningBodyAffiliation affiliation, @MappingTarget GoverningBodyAffiliationsRecord record);
}
