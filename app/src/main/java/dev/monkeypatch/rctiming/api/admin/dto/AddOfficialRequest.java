package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.user.OfficialService;
import dev.monkeypatch.rctiming.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** The body of {@code POST}: a new official's email, name, password (8 or more characters) and roles. */
public record AddOfficialRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(min = OfficialService.MIN_PASSWORD_LENGTH) String password,
        @NotEmpty Set<Role> roles
) {}
