package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Turns a module off for one event, or lets the event use it again (#797). */
public record SetEventModuleRequest(@NotNull Boolean off) {}
