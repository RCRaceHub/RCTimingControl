package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClubProfileRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String websiteUrl,
        Double latitude,
        Double longitude,
        @NotBlank String timezone) {
}
