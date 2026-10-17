package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/tracks}. {@code trackLength} is optional and the unit is not
 * checked.
 */
public record CreateTrackRequest(
        @NotBlank @Size(max = 255) String name,
        String venueNotes,
        Double trackLength) {
}
