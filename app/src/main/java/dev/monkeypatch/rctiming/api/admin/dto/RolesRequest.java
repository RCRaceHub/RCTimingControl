package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.user.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** The body of {@code PUT /{id}/roles}: the official's full set of roles, which replaces the old one. */
public record RolesRequest(@NotEmpty Set<Role> roles) {}
