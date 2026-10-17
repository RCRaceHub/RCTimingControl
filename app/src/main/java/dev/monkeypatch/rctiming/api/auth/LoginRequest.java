package dev.monkeypatch.rctiming.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** The body of {@code POST /api/v1/auth/login}: both fields are required and the email must be well formed. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
