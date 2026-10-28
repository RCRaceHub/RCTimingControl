package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT}: the address of the Entry Export file, its access token (see {@code save}) and whether
 * the app fetches it by itself (null counts as off).
 */
public record SaveRequest(@Size(max = 2000) String url, @Size(max = 2048) String token, Boolean autoFetch) {}
