package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.CompetitorAuditLogRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps a competitor audit log between entity and row; MapStruct generates the implementation, which
 * {@link CompetitorAuditLogRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface CompetitorAuditLogMapper {

    CompetitorAuditLog toEntity(CompetitorAuditLogRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(CompetitorAuditLog log, @MappingTarget CompetitorAuditLogRecord record);
}
