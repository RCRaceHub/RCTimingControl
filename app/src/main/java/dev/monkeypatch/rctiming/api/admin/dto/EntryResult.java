package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/**
 * The answer of {@code POST /api/v1/admin/entries}: the walk-in entry that was added, and warnings such as a
 * transponder number another entry in the event already uses.
 */
public record EntryResult(EntryDto entry, List<String> warnings) {
}
