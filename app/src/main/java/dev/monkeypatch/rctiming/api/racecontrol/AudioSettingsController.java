package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.AudioSettingsDto;
import dev.monkeypatch.rctiming.api.racecontrol.dto.AudioSettingsPatch;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The club's announcer settings (AUDIO-07), under {@code /api/v1/race-control/settings/audio}: the one place they
 * are read and changed, by race control's panel during a race day and by the admin audio page alike. Needs the
 * RACE_DIRECTOR or ADMIN role. Every change is recorded in the audit log. The club's own sound files are uploaded
 * under {@code /api/v1/admin/audio/horns} ({@code AdminAudioController}).
 */
@RestController
@RequestMapping("/api/v1/race-control/settings/audio")
@DirectorOrAdmin
public class AudioSettingsController {

    private final ClubProfileService clubProfileService;
    private final PiperTtsClient piperClient;

    public AudioSettingsController(ClubProfileService clubProfileService, PiperTtsClient piperClient) {
        this.clubProfileService = clubProfileService;
        this.piperClient = piperClient;
    }

    /**
     * Returns the current audio settings from the club profile. {@code GET /api/v1/race-control/settings/audio}.
     */
    @GetMapping
    public ResponseEntity<AudioSettingsDto> getSettings() {
        return ResponseEntity.ok(AudioSettingsDto.of(
                clubProfileService.audioSettings(), clubProfileService.defaultVoiceId().orElse("")));
    }

    /**
     * Updates and persists audio settings to the club profile, for race control's panel and the admin audio page.
     * Only fields included in the request body are applied (see {@link AudioSettingsPatch}); the answer is the
     * settings as saved.
     * {@code PATCH /api/v1/race-control/settings/audio}; 400 if a value is out of range. Recorded in the audit log
     * with the values before and after; when a change alters what the pre-generated clips say, an
     * {@code AudioSettingsChangedEvent} is published after the commit.
     */
    @Audited("audit_log")
    @PatchMapping
    public ResponseEntity<AudioSettingsDto> updateSettings(@RequestBody @Valid AudioSettingsPatch patch) {
        ClubProfile saved = clubProfileService.changeAudioSettings(CurrentOfficial.actor(), profile -> {
            profile.setAudioSettings(patch.applyTo(profile.getAudioSettings()));
            // No voice named: keep the one stored (the column is NOT NULL)
            if (patch.hasVoice()) {
                profile.setDefaultVoiceId(patch.defaultVoiceId());
            }
        });
        return ResponseEntity.ok(AudioSettingsDto.of(saved.getAudioSettings(), saved.getDefaultVoiceId()));
    }

    /**
     * A short spoken sample from the club's Piper voice, for the test button (#286): the audio if Piper answered,
     * 503 if it is switched off or cannot be reached, so the official learns whether the race calls will be
     * spoken by Piper or fall back to the browser voice.
     * {@code GET /api/v1/race-control/settings/audio/piper-test}, answered as {@code audio/wav}. Changes nothing.
     */
    @GetMapping(value = "/piper-test", produces = "audio/wav")
    public ResponseEntity<byte[]> piperTest() {
        try {
            byte[] wav = piperClient.synthesize("Testing the Piper voice. Race control online.",
                    clubProfileService.defaultVoiceId().orElse(null));
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(wav);
        } catch (TtsUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
