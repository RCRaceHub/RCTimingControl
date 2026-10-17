package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.ClubProfile;

/**
 * The club profile, the answer of {@code GET} and {@code PUT /api/v1/admin/club/profile}. Before the club step of setup
 * is done it is a blank profile with no id, an empty name and the time zone {@code UTC}. {@code timezone} is an IANA
 * zone id; {@code logoUrl} is where the uploaded logo is served from, or null.
 */
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
