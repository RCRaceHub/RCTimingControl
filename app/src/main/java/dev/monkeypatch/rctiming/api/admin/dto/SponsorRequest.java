package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of a create or rename. */
public record SponsorRequest(@NotBlank @Size(max = 255) String name) {}
