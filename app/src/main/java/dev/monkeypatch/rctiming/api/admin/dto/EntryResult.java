package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/**
 * The answer of {@code POST /api/v1/admin/entries}: the walk-in entry that was added, and warnings such as a
 * transponder number another entry in the event already uses. {@code competitorId} is the driver the entry is for,
 * so the caller can enter the same driver in another class without typing the name again.
 */
public record EntryResult(EntryDto entry, Long competitorId, List<String> warnings) {
}
