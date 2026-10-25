package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubSettingsService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The club settings file (#510) under {@code /api/v1/admin/club/settings}: download this install's settings, and read a
 * file from another install back in, with a preview first. ADMIN only. The file never holds the sign-in key,
 * officials, race history or the RaceHub address; see {@link ClubSettingsService}.
 */
@RestController
@RequestMapping("/api/v1/admin/club/settings")
@PreAuthorize("hasRole('ADMIN')")
public class ClubSettingsController {

    private final ClubSettingsService settings;

    public ClubSettingsController(ClubSettingsService settings) {
        this.settings = settings;
    }

    /** This install's settings as a JSON download. {@code GET /export}; read only. */
    @GetMapping("/export")
    public ResponseEntity<ClubSettingsService.SettingsFile> export() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("rctiming-club-settings.json").build().toString())
                .body(settings.export());
    }

    /**
     * Previews, or with {@code apply=true} imports, a settings file. {@code POST /import}; the body is the file.
     * Answers the sections in the file and which of them would change (or did change) anything. 400 for a file that is
     * not a settings file, is from another version, or holds an invalid setting; then nothing is changed. Audited when
     * it applies.
     */
    @Audited("audit_log")
    @PostMapping("/import")
    public List<ClubSettingsService.Section> importFile(Authentication auth,
                                                        @RequestBody ClubSettingsService.SettingsFile file,
                                                        @RequestParam(defaultValue = "false") boolean apply) {
        return apply ? settings.apply(CurrentOfficial.actor(auth), file) : settings.preview(file);
    }
}
