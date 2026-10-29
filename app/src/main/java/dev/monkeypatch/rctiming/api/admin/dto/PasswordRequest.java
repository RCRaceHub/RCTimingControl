package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.user.OfficialService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code PUT /{id}/password}: the new password, 8 or more characters. */
public record PasswordRequest(@NotNull @Size(min = OfficialService.MIN_PASSWORD_LENGTH) String password) {}
