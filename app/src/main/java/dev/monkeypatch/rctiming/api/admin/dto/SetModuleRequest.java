package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Switches a module on or off for the whole club (#797). */
public record SetModuleRequest(@NotNull Boolean enabled) {}
