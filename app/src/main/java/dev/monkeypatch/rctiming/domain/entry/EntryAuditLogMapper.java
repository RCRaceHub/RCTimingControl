package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Maps an entry audit log between entity and row; MapStruct generates the implementation, which
 * {@link EntryAuditLogRepository} uses.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryAuditLogMapper {

    EntryAuditLog toEntity(EntryAuditLogRecord record);

    /** Copies every column except the id, which the repository handles, into the record. */
    @Mapping(target = "id", ignore = true)
    void toRecord(EntryAuditLog log, @MappingTarget EntryAuditLogRecord record);
}
