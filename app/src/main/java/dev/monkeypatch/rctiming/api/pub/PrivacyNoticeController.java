package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The club's privacy notice (#267). It is open to anyone, so the link on the public pages and the sign-in
 * page works without an account; it holds the club's name and contact details, which the club already
 * publishes, and no personal data.
 */
@RestController
@RequestMapping("/api/v1/privacy-notice")
public class PrivacyNoticeController {

    private final ClubProfileService clubProfileService;

    public PrivacyNoticeController(ClubProfileService clubProfileService) {
        this.clubProfileService = clubProfileService;
    }

    @GetMapping
    public PrivacyNoticeDto get() {
        ClubProfileService.PrivacyNotice n = clubProfileService.privacyNotice();
        return new PrivacyNoticeDto(n.clubName(), n.email(), n.phone(), n.text());
    }

    public record PrivacyNoticeDto(String clubName, String email, String phone, String text) {}
}
