package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Club-wide announcer settings (AUDIO-07), for admins and race directors. The PUT here replaces the toggles and
 * the default voice; the countdown intervals are not part of it and are left as they are. Race control's
 * {@code AudioSettingsController} updates any of the settings, intervals included.
 */
@RestController
@RequestMapping("/api/v1/admin/audio")
public class AdminAudioController {

    private final ClubProfileService clubProfileService;

    public AdminAudioController(ClubProfileService clubProfileService) {
        this.clubProfileService = clubProfileService;
    }

    /** DTO for club-wide audio toggle settings */
    public record AudioSettingsDto(
            boolean announceCountdown,
            boolean announceStagger,
            boolean announceLapBeep,
            boolean announceFinish,
            boolean announceRunningOrder,
            @Min(1) @Max(20) int runningOrderDepth,
            String defaultVoiceId
    ) {
        static AudioSettingsDto of(ClubAudioSettings s, String defaultVoiceId) {
            return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                    s.announceFinish(), s.announceRunningOrder(), s.runningOrderDepth(), defaultVoiceId);
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
            profile.setAudioSettings(new ClubAudioSettings(
                    dto.announceCountdown(),
                    dto.announceStagger(),
                    dto.announceLapBeep(),
                    dto.announceFinish(),
                    dto.announceRunningOrder(),
                    dto.runningOrderDepth(),
                    profile.getAudioSettings().countdownIntervals()  // not part of this form: keep them
            ));
            // No voice chosen on the page: keep the one stored (the column is NOT NULL)
            if (dto.defaultVoiceId() != null && !dto.defaultVoiceId().isBlank()) {
                profile.setDefaultVoiceId(dto.defaultVoiceId());
            }
        });
        return ResponseEntity.ok(dto);
    }
}
