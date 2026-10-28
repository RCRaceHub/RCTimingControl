package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.api.admin.dto.ClubProfileDto;
import dev.monkeypatch.rctiming.api.admin.dto.CreateClubProfileRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CreateGoverningBodyRequest;
import dev.monkeypatch.rctiming.api.admin.dto.GoverningBodyAffiliationDto;
import dev.monkeypatch.rctiming.api.admin.dto.LiveTimingSettingsDto;
import dev.monkeypatch.rctiming.api.admin.dto.LogoUploadResponse;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.LogoUploadService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * The club's profile under {@code /api/v1/admin/club}: its details and logo, the privacy notice and the governing
 * body affiliations. Reads are open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN only and recorded in
 * the audit log.
 */
@RestController
@RequestMapping("/api/v1/admin/club")
@RaceControlStaff
public class ClubProfileController {

    private final ClubProfileService clubProfileService;
    private final LogoUploadService logoUploadService;

    public ClubProfileController(ClubProfileService clubProfileService,
                                  LogoUploadService logoUploadService) {
        this.clubProfileService = clubProfileService;
        this.logoUploadService = logoUploadService;
    }

    /**
     * The club profile, or a blank one (empty name, UTC) before the club step of setup is done.
     * {@code GET /profile}; read only.
     */
    @GetMapping("/profile")
    public ClubProfileDto getProfile() {
        return clubProfileService.getProfile()
                .map(ClubProfileDto::from)
                .orElseGet(() -> new ClubProfileDto(null, "", null, null, null, null, null, "UTC", null));
    }

    /**
     * Creates the club profile, or replaces its details. {@code PUT /profile}; ADMIN. 400 for a bad body or a time
     * zone that is not valid. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/profile")
    @AdminOnly
    public ClubProfileDto createOrUpdateProfile(@RequestBody @Valid CreateClubProfileRequest request) {
        return ClubProfileDto.from(clubProfileService.createOrUpdateProfile(CurrentOfficial.actor(),
                new ClubProfileService.ClubDetails(request.name(), request.email(), request.phone(),
                        request.websiteUrl(), request.latitude(), request.longitude(), request.timezone())));
    }

    /** The privacy notice text as the editor needs it. {@code GET /privacy-notice}; read only. */
    @GetMapping("/privacy-notice")
    public PrivacyNoticeEditDto getPrivacyNotice() {
        return PrivacyNoticeEditDto.from(clubProfileService.privacyNotice());
    }

    /**
     * Saves the club's own privacy notice; a blank text goes back to the default. {@code PUT /privacy-notice};
     * ADMIN; 400 over 20000 characters. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/privacy-notice")
    @AdminOnly
    public PrivacyNoticeEditDto changePrivacyNotice(@RequestBody @Valid PrivacyNoticeRequest request) {
        return PrivacyNoticeEditDto.from(
                clubProfileService.changePrivacyNotice(CurrentOfficial.actor(), request.text()));
    }

    /** The live timing thresholds (#505). {@code GET /live-timing-settings}; read only. */
    @GetMapping("/live-timing-settings")
    public LiveTimingSettingsDto getLiveTimingSettings() {
        return LiveTimingSettingsDto.from(clubProfileService.liveTimingSettings());
    }

    /**
     * Saves the live timing thresholds. {@code PUT /live-timing-settings}; ADMIN; 400 for a value out of range or a
     * gaining-fast limit below the trend limit. Races in progress take them from their next update. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/live-timing-settings")
    @AdminOnly
    public LiveTimingSettingsDto changeLiveTimingSettings(@RequestBody @Valid LiveTimingSettingsDto request) {
        return LiveTimingSettingsDto.from(
                clubProfileService.changeLiveTimingSettings(CurrentOfficial.actor(), request.toSettings()));
    }

    /** The governing bodies the club is affiliated to. {@code GET /affiliations}; read only. */
    @GetMapping("/affiliations")
    public List<GoverningBodyAffiliationDto> listAffiliations() {
        return clubProfileService.listAffiliations().stream().map(GoverningBodyAffiliationDto::from).toList();
    }

    /** Adds a governing body. {@code POST /affiliations}; ADMIN; answers 201. Audited. */
    @Audited("audit_log")
    @PostMapping("/affiliations")
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public GoverningBodyAffiliationDto createAffiliation(@RequestBody @Valid CreateGoverningBodyRequest request) {
        return GoverningBodyAffiliationDto.from(clubProfileService.createAffiliation(CurrentOfficial.actor(),
                request.code(), request.displayName(), request.membershipRequired()));
    }

    /** Changes a governing body. {@code PUT /affiliations/{id}}; ADMIN. Audited. */
    @Audited("audit_log")
    @PutMapping("/affiliations/{id}")
    @AdminOnly
    public GoverningBodyAffiliationDto updateAffiliation(@PathVariable Long id,
                                                          @RequestBody @Valid CreateGoverningBodyRequest request) {
        return GoverningBodyAffiliationDto.from(clubProfileService.updateAffiliation(CurrentOfficial.actor(), id,
                request.code(), request.displayName(), request.membershipRequired()));
    }

    /** Removes a governing body. {@code DELETE /affiliations/{id}}; ADMIN; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/affiliations/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAffiliation(@PathVariable Long id) {
        clubProfileService.deleteAffiliation(CurrentOfficial.actor(), id);
    }

    /**
     * Replaces the club logo with the uploaded image. {@code PUT /logo} as multipart form data with a {@code file}
     * part; ADMIN. 400 for a missing, oversized or unsupported file. Answers with the URL the logo is served from.
     * Audited.
     */
    @Audited("audit_log")
    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminOnly
    public LogoUploadResponse uploadLogo(@RequestPart("file") MultipartFile file) {
        Long profileId = clubProfileService.getSingletonProfileId();
        String url = logoUploadService.uploadLogo(CurrentOfficial.actor(), profileId, file);
        return new LogoUploadResponse(url);
    }

    /** The body of {@code PUT /privacy-notice}. {@code text} blank means go back to the default notice. */
    public record PrivacyNoticeRequest(@Size(max = 20000) String text) {}

    /**
     * The privacy notice as the editor shows it. {@code customised}: whether {@code text} is the club's own, or the
     * default.
     */
    public record PrivacyNoticeEditDto(String text, boolean customised) {
        static PrivacyNoticeEditDto from(ClubProfileService.PrivacyNotice n) {
            return new PrivacyNoticeEditDto(n.text(), n.customised());
        }
    }
}
