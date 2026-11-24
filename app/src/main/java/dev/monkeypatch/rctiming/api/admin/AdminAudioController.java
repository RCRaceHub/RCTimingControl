package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.HornSoundService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The club's own sound files for the announcer (AUDIO-07), for admins and race directors, under
 * {@code /api/v1/admin/audio}: upload and removal of the horns, the tick and the lap beeps. The announcer settings
 * themselves are read and changed at {@code /api/v1/race-control/settings/audio} ({@code AudioSettingsController}).
 * Every endpoint needs the ADMIN or RACE_DIRECTOR role. Changes are recorded in the audit log.
 */
@RestController
@RequestMapping("/api/v1/admin/audio")
public class AdminAudioController {

    private final HornSoundService hornSoundService;

    public AdminAudioController(HornSoundService hornSoundService) {
        this.hornSoundService = hornSoundService;
    }

    /**
     * Replaces a horn, the tick or a lap beep with the club's own sound file (#278, #281, #299).
     * {@code PUT /horns/{horn}} as multipart form data; ADMIN or RACE_DIRECTOR. {@code horn} names the sound
     * ({@code start}, {@code finish}, {@code tick}, {@code lap-beep}, {@code faster-beep} or
     * {@code slower-beep}); {@code file} is the sound (at most 1 MB) and {@code durationMs} how long it plays, so
     * race control can wait that long before a spoken line. 400 for a missing, oversized or unsupported file or a
     * duration outside 100 to 10000 ms;
     * 404 for a horn name that is not one of those. Answers with the club's sounds as saved.
     */
    @Audited("audit_log")
    @PutMapping(value = "/horns/{horn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @DirectorOrAdmin
    public ClubAudioSettings.HornSounds uploadHorn(@PathVariable String horn,
                                                    @RequestPart("file") MultipartFile file,
                                                    @RequestParam int durationMs) {
        return hornSoundService.upload(CurrentOfficial.actor(), HornSoundService.Horn.of(horn), file, durationMs)
                .hornSounds();
    }

    /**
     * Goes back to the built-in sound. {@code DELETE /horns/{horn}}, named as for the upload; ADMIN or
     * RACE_DIRECTOR. Answers with the club's sounds as saved.
     */
    @Audited("audit_log")
    @DeleteMapping("/horns/{horn}")
    @DirectorOrAdmin
    public ClubAudioSettings.HornSounds removeHorn(@PathVariable String horn) {
        return hornSoundService.remove(CurrentOfficial.actor(), HornSoundService.Horn.of(horn)).hornSounds();
    }
}
