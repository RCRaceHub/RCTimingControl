package dev.monkeypatch.rctiming.domain.audit;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.AuditLogRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an audit entry between entity and row; MapStruct generates the implementation, which
 * {@link AuditLogRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AuditEntryMapper {

    AuditEntry toEntity(AuditLogRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(AuditEntry e, @MappingTarget AuditLogRecord record);
}
