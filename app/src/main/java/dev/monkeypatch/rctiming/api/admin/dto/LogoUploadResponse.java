package dev.monkeypatch.rctiming.api.admin.dto;

/**
 * The answer of {@code PUT /api/v1/admin/club/logo}: the URL the new logo is served from.
 */
public record LogoUploadResponse(String logoUrl) {}
