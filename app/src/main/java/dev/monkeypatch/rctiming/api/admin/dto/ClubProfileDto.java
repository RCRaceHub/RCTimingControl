package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.ClubProfile;

public record ClubProfileDto(
        Long id,
        String name,
        String email,
        String phone,
        String websiteUrl,
        Double latitude,
        Double longitude,
        String timezone,
        String logoUrl) {

    public static ClubProfileDto from(ClubProfile p) {
        return new ClubProfileDto(
                p.getId(),
                p.getName(),
                p.getEmail(),
                p.getPhone(),
                p.getWebsiteUrl(),
                p.getLatitude(),
                p.getLongitude(),
                p.getTimezone(),
                p.getLogoUrl());
    }
}
