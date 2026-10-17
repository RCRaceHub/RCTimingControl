package dev.monkeypatch.rctiming.api.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * The body of {@code POST /api/v1/setup/staff}: a new official's name, email, password (8 or more characters) and
 * at least one role.
 *
 * @param roles role names ({@code ADMIN}, {@code RACE_DIRECTOR}, {@code REFEREE}); a name that is not a role is a 400
 */
public record SetupStaffRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        @NotEmpty Set<String> roles
) {}
