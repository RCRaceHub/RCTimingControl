package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The body of {@code PUT /setting}. */
public record TlsSettingRequest(@NotNull Boolean enabled, @Min(1) @Max(65535) Integer port) {}
