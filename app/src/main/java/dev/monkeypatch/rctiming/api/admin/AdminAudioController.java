package dev.monkeypatch.rctiming.api.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.HornSoundService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Club-wide announcer settings (AUDIO-07), for admins and race directors. The PUT here replaces the toggles and
 * the default voice; the countdown intervals, wording and tuning are kept when it leaves them out. Under
 * {@code /api/v1/admin/audio}; every endpoint needs the ADMIN or RACE_DIRECTOR role. Changes are recorded in the
 * audit log. Race control has a smaller patch-style endpoint for the same settings
 * ({@code AudioSettingsController}).
 */
@RestController
@RequestMapping("/api/v1/admin/audio")
public class AdminAudioController {

    private final ClubProfileService clubProfileService;
    private final HornSoundService hornSoundService;

    public AdminAudioController(ClubProfileService clubProfileService, HornSoundService hornSoundService) {
        this.clubProfileService = clubProfileService;
        this.hornSoundService = hornSoundService;
    }

    /**
     * The club-wide audio settings: the body of {@code PUT /settings} and its answer, and the answer of
     * {@code GET /settings}. It mirrors {@link ClubAudioSettings}, which says what each switch does, plus the club's
     * default Piper voice. {@code defaultVoiceId} is an empty string on a read until a voice is chosen, and on a
     * write a null or blank one keeps the stored voice. On a write, a null optional field keeps its saved value;
     * {@code hornSounds} is ignored on a write, since the sound files change only by upload or removal. Limits
     * checked on the way in (400 if broken): {@code runningOrderPercent} 1 to 100, {@code runningOrderAllUpTo} 0 to
     * 100, {@code startCountdownSeconds} 0 to 60, {@code countdownIntervals} at most 10 times of 1 to 3600 seconds,
     * {@code timeUpText} at most 200 characters.
     */
    public record AudioSettingsDto(
            boolean announceCountdown,
            boolean announceStagger,
            boolean announceLapBeep,
            boolean announceFinish,
            boolean announceRunningOrder,
            @Min(1) @Max(100) Integer runningOrderPercent,
            @Min(0) @Max(100) Integer runningOrderAllUpTo,
            String defaultVoiceId,
            @Size(max = 10) int[] countdownIntervals,
            Boolean startHorn,
            Boolean finishHorn,
            Boolean announceRaceCalls,
            Boolean announceNextRace,
            @Min(0) @Max(60) Integer startCountdownSeconds,
            ClubAudioSettings.HornSounds hornSounds,
            Boolean announceCarFinish,
            @Size(max = 200) String timeUpText,
            Boolean tickSound,
            Boolean finalBeeps,
            @Valid ClubAudioSettings.Wording wording,
            @Valid ClubAudioSettings.Tuning tuning,
            Boolean paceBeeps
    ) {
        /** Each interval is a clip the server makes, so each must be from a second up to an hour. */
        @JsonIgnore
        @AssertTrue(message = "countdownIntervals must each be from 1 to 3600 seconds")
        public boolean isCountdownIntervalsInRange() {
            return ClubAudioSettings.intervalsInRange(countdownIntervals);
        }

        static AudioSettingsDto of(ClubAudioSettings s, String defaultVoiceId) {
            return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                    s.announceFinish(), s.announceRunningOrder(), s.runningOrderPercent(), s.runningOrderAllUpTo(),
                    defaultVoiceId, s.countdownIntervals(),
                    s.startHorn(), s.finishHorn(), s.announceRaceCalls(), s.announceNextRace(),
                    s.startCountdownSeconds(), s.hornSounds(), s.announceCarFinish(), s.timeUpText(), s.tickSound(),
                    s.finalBeeps(), s.wording(), s.tuning(), s.paceBeeps());
        }
    }

    /** The club's audio settings and default voice. {@code GET /settings}; ADMIN or RACE_DIRECTOR. */
    @GetMapping("/settings")
    @DirectorOrAdmin
    public ResponseEntity<AudioSettingsDto> getAudioSettings() {
        // "" until a voice is chosen, so the page's select shows its placeholder
        return ResponseEntity.ok(AudioSettingsDto.of(
                clubProfileService.audioSettings(), clubProfileService.defaultVoiceId().orElse("")));
    }

    /**
     * Saves the toggles and the default voice. {@code PUT /settings}; ADMIN or RACE_DIRECTOR; 400 if a value is out
     * of range. Answers with the body as sent, not re-read. Recorded in the audit log with the values before and
     * after.
     */
    @Audited("audit_log")
    @PutMapping("/settings")
    @DirectorOrAdmin
    public ResponseEntity<AudioSettingsDto> saveAudioSettings(@RequestBody @Valid AudioSettingsDto dto) {
        clubProfileService.changeAudioSettings(CurrentOfficial.actor(), profile -> {
            ClubAudioSettings kept = profile.getAudioSettings();
            profile.setAudioSettings(new ClubAudioSettings(
                    dto.announceCountdown(),
                    dto.announceStagger(),
                    dto.announceLapBeep(),
                    dto.announceFinish(),
                    dto.announceRunningOrder(),
                    kept.runningOrderDepth(),  // no longer used, kept as saved
                    dto.countdownIntervals() != null ? dto.countdownIntervals() : kept.countdownIntervals(),
                    // a field the form leaves out keeps its saved value
                    dto.startHorn() != null ? dto.startHorn() : kept.startHorn(),
                    dto.finishHorn() != null ? dto.finishHorn() : kept.finishHorn(),
                    dto.announceRaceCalls() != null ? dto.announceRaceCalls() : kept.announceRaceCalls(),
                    dto.announceNextRace() != null ? dto.announceNextRace() : kept.announceNextRace(),
                    dto.startCountdownSeconds() != null ? dto.startCountdownSeconds() : kept.startCountdownSeconds(),
                    kept.hornSounds(),  // changed only by uploading or removing a sound
                    dto.announceCarFinish() != null ? dto.announceCarFinish() : kept.announceCarFinish(),
                    dto.timeUpText() != null ? dto.timeUpText() : kept.timeUpText(),
                    dto.tickSound() != null ? dto.tickSound() : kept.tickSound(),
                    dto.finalBeeps() != null ? dto.finalBeeps() : kept.finalBeeps(),
                    dto.wording() != null ? dto.wording() : kept.wording(),
                    dto.tuning() != null ? dto.tuning() : kept.tuning(),
                    dto.runningOrderPercent() != null ? dto.runningOrderPercent() : kept.runningOrderPercent(),
                    dto.runningOrderAllUpTo() != null ? dto.runningOrderAllUpTo() : kept.runningOrderAllUpTo(),
                    dto.paceBeeps() != null ? dto.paceBeeps() : kept.paceBeeps()
            ));
            // No voice chosen on the page: keep the one stored (the column is NOT NULL)
            if (dto.defaultVoiceId() != null && !dto.defaultVoiceId().isBlank()) {
                profile.setDefaultVoiceId(dto.defaultVoiceId());
            }
        });
        return ResponseEntity.ok(dto);
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
