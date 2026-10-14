package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.api.admin.dto.ClubProfileDto;
import dev.monkeypatch.rctiming.api.admin.dto.CreateClubProfileRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CreateGoverningBodyRequest;
import dev.monkeypatch.rctiming.api.admin.dto.GoverningBodyAffiliationDto;
import dev.monkeypatch.rctiming.api.admin.dto.LogoUploadResponse;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.LogoUploadService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/club")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class ClubProfileController {

    private final ClubProfileService clubProfileService;
    private final LogoUploadService logoUploadService;

    public ClubProfileController(ClubProfileService clubProfileService,
                                  LogoUploadService logoUploadService) {
        this.clubProfileService = clubProfileService;
        this.logoUploadService = logoUploadService;
    }

    @GetMapping("/profile")
    public ClubProfileDto getProfile() {
        return clubProfileService.getProfile()
                .map(ClubProfileDto::from)
                .orElseGet(() -> new ClubProfileDto(null, "", null, null, null, null, null, "UTC", null));
    }

    @Audited("audit_log")
    @PutMapping("/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ClubProfileDto createOrUpdateProfile(Authentication auth, @RequestBody @Valid CreateClubProfileRequest request) {
        return ClubProfileDto.from(clubProfileService.createOrUpdateProfile(CurrentOfficial.actor(auth),
                new ClubProfileService.ClubDetails(request.name(), request.email(), request.phone(),
                        request.websiteUrl(), request.latitude(), request.longitude(), request.timezone())));
    }

    @GetMapping("/privacy-notice")
    public PrivacyNoticeEditDto getPrivacyNotice() {
        return PrivacyNoticeEditDto.from(clubProfileService.privacyNotice());
    }

    @Audited("audit_log")
    @PutMapping("/privacy-notice")
    @PreAuthorize("hasRole('ADMIN')")
    public PrivacyNoticeEditDto changePrivacyNotice(Authentication auth,
                                                    @RequestBody @Valid PrivacyNoticeRequest request) {
        return PrivacyNoticeEditDto.from(
                clubProfileService.changePrivacyNotice(CurrentOfficial.actor(auth), request.text()));
    }

    @GetMapping("/affiliations")
    public List<GoverningBodyAffiliationDto> listAffiliations() {
        return clubProfileService.listAffiliations().stream().map(GoverningBodyAffiliationDto::from).toList();
    }

    @Audited("audit_log")
    @PostMapping("/affiliations")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public GoverningBodyAffiliationDto createAffiliation(Authentication auth,
                                                          @RequestBody @Valid CreateGoverningBodyRequest request) {
        return GoverningBodyAffiliationDto.from(clubProfileService.createAffiliation(CurrentOfficial.actor(auth),
                request.code(), request.displayName(), request.membershipRequired()));
    }

    @Audited("audit_log")
    @PutMapping("/affiliations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GoverningBodyAffiliationDto updateAffiliation(Authentication auth, @PathVariable Long id,
                                                          @RequestBody @Valid CreateGoverningBodyRequest request) {
        return GoverningBodyAffiliationDto.from(clubProfileService.updateAffiliation(CurrentOfficial.actor(auth), id,
                request.code(), request.displayName(), request.membershipRequired()));
    }

    @Audited("audit_log")
    @DeleteMapping("/affiliations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAffiliation(Authentication auth, @PathVariable Long id) {
        clubProfileService.deleteAffiliation(CurrentOfficial.actor(auth), id);
    }

    @Audited("audit_log")
    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public LogoUploadResponse uploadLogo(Authentication auth, @RequestPart("file") MultipartFile file) {
        Long profileId = clubProfileService.getSingletonProfileId();
        String url = logoUploadService.uploadLogo(CurrentOfficial.actor(auth), profileId, file);
        return new LogoUploadResponse(url);
    }

    /** {@code text} blank means go back to the default notice. */
    public record PrivacyNoticeRequest(@Size(max = 20000) String text) {}

    /** {@code customised}: whether {@code text} is the club's own, or the default. */
    public record PrivacyNoticeEditDto(String text, boolean customised) {
        static PrivacyNoticeEditDto from(ClubProfileService.PrivacyNotice n) {
            return new PrivacyNoticeEditDto(n.text(), n.customised());
        }
    }
}
