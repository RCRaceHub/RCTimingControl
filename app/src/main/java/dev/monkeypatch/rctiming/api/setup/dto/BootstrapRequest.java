package dev.monkeypatch.rctiming.api.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/setup/bootstrap}: the first admin's name, email and password (8 or more
 * characters).
 */
public record BootstrapRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password
) {}
