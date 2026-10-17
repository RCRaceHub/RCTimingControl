package dev.monkeypatch.rctiming.query.audit;

import java.util.List;

/**
 * One page of audit rows, newest first, with how many rows match in all.
 *
 * @param page the zero-based page number actually used (after clamping)
 * @param size the page size actually used, capped at {@link AuditQueryService#MAX_PAGE_SIZE}
 */
public record AuditPageDto(List<AuditEntryDto> entries, long total, int page, int size) {
}
