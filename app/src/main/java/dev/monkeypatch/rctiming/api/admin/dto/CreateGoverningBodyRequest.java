package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of the club endpoints that add or change a governing body affiliation (for example BRCA). {@code code} is the
 * short code and {@code membershipRequired} says whether a competitor must hold a membership number.
 */
public record CreateGoverningBodyRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String displayName,
        boolean membershipRequired) {
}
