package dev.monkeypatch.rctiming.api.setup.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT}: a host name such as {@code rctiming} or {@code rctiming.local}; blank switches the
 * name off.
 */
public record NetworkNameRequest(@NotNull String name) {
}
