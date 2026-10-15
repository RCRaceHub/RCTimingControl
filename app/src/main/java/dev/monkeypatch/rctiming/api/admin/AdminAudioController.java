package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.HornSoundService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

import java.util.Arrays;

/**
 * Club-wide announcer settings (AUDIO-07), for admins and race directors. The PUT here replaces the toggles and
 * the default voice; the countdown intervals, wording and tuning are kept when it leaves them out.
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

    /** DTO for club-wide audio toggle settings */
    public record AudioSettingsDto(
            boolean announceCountdown,
            boolean announceStagger,
            boolean announceLapBeep,
            boolean announceFinish,
            boolean announceRunningOrder,
            @Min(1) @Max(20) int runningOrderDepth,
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
            @Valid ClubAudioSettings.Tuning tuning
    ) {
        /** Each interval is a clip the server makes, so each must be from a second up to an hour. */
        @JsonIgnore
        @AssertTrue(message = "countdownIntervals must each be from 1 to 3600 seconds")
        public boolean isCountdownIntervalsInRange() {
            return countdownIntervals == null || Arrays.stream(countdownIntervals).allMatch(i -> i >= 1 && i <= 3600);
        }

        static AudioSettingsDto of(ClubAudioSettings s, String defaultVoiceId) {
            return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                    s.announceFinish(), s.announceRunningOrder(), s.runningOrderDepth(), defaultVoiceId,
                    s.countdownIntervals(),
                    s.startHorn(), s.finishHorn(), s.announceRaceCalls(), s.announceNextRace(),
                    s.startCountdownSeconds(), s.hornSounds(), s.announceCarFinish(), s.timeUpText(), s.tickSound(),
                    s.finalBeeps(), s.wording(), s.tuning());
        }
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<AudioSettingsDto> getAudioSettings() {
        // "" until a voice is chosen, so the page's select shows its placeholder
        return ResponseEntity.ok(AudioSettingsDto.of(
                clubProfileService.audioSettings(), clubProfileService.defaultVoiceId().orElse("")));
    }

    @Audited("audit_log")
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ResponseEntity<AudioSettingsDto> saveAudioSettings(Authentication auth,
                                                              @RequestBody @Valid AudioSettingsDto dto) {
        clubProfileService.changeAudioSettings(CurrentOfficial.actor(auth), profile -> {
            ClubAudioSettings kept = profile.getAudioSettings();
            profile.setAudioSettings(new ClubAudioSettings(
                    dto.announceCountdown(),
                    dto.announceStagger(),
                    dto.announceLapBeep(),
                    dto.announceFinish(),
                    dto.announceRunningOrder(),
                    dto.runningOrderDepth(),
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
                    dto.tuning() != null ? dto.tuning() : kept.tuning()
            ));
            // No voice chosen on the page: keep the one stored (the column is NOT NULL)
            if (dto.defaultVoiceId() != null && !dto.defaultVoiceId().isBlank()) {
                profile.setDefaultVoiceId(dto.defaultVoiceId());
            }
        });
        return ResponseEntity.ok(dto);
    }

    /** Replaces the start or finish horn, the tick or the lap beep with the club's own sound file (#278, #281, #299). */
    @Audited("audit_log")
    @PutMapping(value = "/horns/{horn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ClubAudioSettings.HornSounds uploadHorn(Authentication auth, @PathVariable String horn,
                                                    @RequestPart("file") MultipartFile file,
                                                    @RequestParam int durationMs) {
        return hornSoundService.upload(CurrentOfficial.actor(auth), HornSoundService.Horn.of(horn), file, durationMs)
                .hornSounds();
    }

    /** Goes back to the built-in sound. */
    @Audited("audit_log")
    @DeleteMapping("/horns/{horn}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public ClubAudioSettings.HornSounds removeHorn(Authentication auth, @PathVariable String horn) {
        return hornSoundService.remove(CurrentOfficial.actor(auth), HornSoundService.Horn.of(horn)).hornSounds();
    }
}
