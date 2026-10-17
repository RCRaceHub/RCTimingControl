package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The body of {@code POST /api/v1/admin/events}. The track is optional (EVENT-07).
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate eventDate,
        Long trackId            // nullable per EVENT-07 (optional)
) {}
