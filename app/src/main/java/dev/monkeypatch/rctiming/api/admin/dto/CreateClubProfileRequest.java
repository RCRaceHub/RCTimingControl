package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/admin/club/profile}. {@code timezone} is an IANA zone id and is refused if it is not
 * one. The location is optional.
 */
public record CreateClubProfileRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String websiteUrl,
        Double latitude,
        Double longitude,
        @NotBlank String timezone) {
}
