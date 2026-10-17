package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.GoverningBodyAffiliation;

/**
 * A governing body the club is affiliated to, the answer of the affiliation endpoints under
 * {@code /api/v1/admin/club/affiliations}.
 */
public record GoverningBodyAffiliationDto(
        Long id,
        String code,
        String displayName,
        boolean membershipRequired) {

    public static GoverningBodyAffiliationDto from(GoverningBodyAffiliation a) {
        return new GoverningBodyAffiliationDto(
                a.getId(),
                a.getCode(),
                a.getDisplayName(),
                a.isMembershipRequired());
    }
}
