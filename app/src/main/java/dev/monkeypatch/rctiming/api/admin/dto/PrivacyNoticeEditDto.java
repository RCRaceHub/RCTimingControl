package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;

/**
 * The privacy notice as the editor shows it. {@code customised}: whether {@code text} is the club's own, or the
 * default.
 */
public record PrivacyNoticeEditDto(String text, boolean customised) {
    public static PrivacyNoticeEditDto from(ClubProfileService.PrivacyNotice n) {
        return new PrivacyNoticeEditDto(n.text(), n.customised());
    }
}
