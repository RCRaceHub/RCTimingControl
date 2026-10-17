package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of {@code POST /api/v1/admin/entries/{id}/withdraw}: why the entry is withdrawn, which is kept on the entry
 * audit row.
 */
public record AdminWithdrawRequest(@NotBlank String reason) {}
