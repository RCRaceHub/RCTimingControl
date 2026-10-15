package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * Race control–facing audio settings endpoint (AUDIO-07).
 * <p>
 * Allows race directors to view and update audio toggle settings during a race day
 * without navigating to the admin panel. Settings are persisted to the club profile
 * so they survive page reloads.
 */
@RestController
@RequestMapping("/api/v1/race-control/settings/audio")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR', 'ADMIN')")
public class AudioSettingsController {

    private final ClubProfileService clubProfileService;
    private final PiperTtsClient piperClient;

    public AudioSettingsController(ClubProfileService clubProfileService, PiperTtsClient piperClient) {
        this.clubProfileService = clubProfileService;
        this.piperClient = piperClient;
    }

    /** The settings as they are now. */
    public record AudioSettingsDto(
            boolean announceCountdown,
            boolean announceStagger,
            boolean announceLapBeep,
            boolean announceFinish,
            boolean announceRunningOrder,
            int runningOrderDepth,
            int[] countdownIntervals,
            boolean startHorn,
            boolean finishHorn,
            boolean announceRaceCalls,
            boolean announceNextRace,
            int startCountdownSeconds,
            ClubAudioSettings.HornSounds hornSounds,
            boolean announceCarFinish,
            String timeUpText,
            boolean tickSound,
            boolean finalBeeps,
            ClubAudioSettings.Wording wording,
            ClubAudioSettings.Tuning tuning
    ) {
        static AudioSettingsDto of(ClubAudioSettings s) {
            return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                    s.announceFinish(), s.announceRunningOrder(), s.runningOrderDepth(), s.countdownIntervals(),
                    s.startHorn(), s.finishHorn(), s.announceRaceCalls(), s.announceNextRace(),
                    s.startCountdownSeconds(), s.hornSounds(), s.announceCarFinish(), s.timeUpText(), s.tickSound(),
                    s.finalBeeps(), s.wording(), s.tuning());
        }
    }

    /**
     * A change to the settings. A field left out (null) keeps its saved value, so a client can send just the
     * toggle it changed.
     */
    public record AudioSettingsPatch(
            Boolean announceCountdown,
            Boolean announceStagger,
            Boolean announceLapBeep,
            Boolean announceFinish,
            Boolean announceRunningOrder,
            @Min(1) @Max(20) Integer runningOrderDepth,
            @Size(max = 10) int[] countdownIntervals,
            Boolean startHorn,
            Boolean finishHorn,
            Boolean announceRaceCalls,
            Boolean announceNextRace,
            @Min(0) @Max(60) Integer startCountdownSeconds,
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
    }

    /**
     * Returns the current audio settings from the club profile.
     */
    @GetMapping
    public ResponseEntity<AudioSettingsDto> getSettings() {
        return ResponseEntity.ok(AudioSettingsDto.of(clubProfileService.audioSettings()));
    }

    /**
     * Updates and persists audio settings to the club profile.
     * Only fields included in the request body are applied; the answer is the settings as saved.
     */
    @Audited("audit_log")
    @PatchMapping
    public ResponseEntity<AudioSettingsDto> updateSettings(Authentication auth,
                                                           @RequestBody @Valid AudioSettingsPatch patch) {
        ClubProfile saved = clubProfileService.changeAudioSettings(CurrentOfficial.actor(auth),
                profile -> {
                    ClubAudioSettings s = profile.getAudioSettings();
                    profile.setAudioSettings(new ClubAudioSettings(
                            patch.announceCountdown() != null ? patch.announceCountdown() : s.announceCountdown(),
                            patch.announceStagger() != null ? patch.announceStagger() : s.announceStagger(),
                            patch.announceLapBeep() != null ? patch.announceLapBeep() : s.announceLapBeep(),
                            patch.announceFinish() != null ? patch.announceFinish() : s.announceFinish(),
                            patch.announceRunningOrder() != null ? patch.announceRunningOrder() : s.announceRunningOrder(),
                            patch.runningOrderDepth() != null ? patch.runningOrderDepth() : s.runningOrderDepth(),
                            patch.countdownIntervals() != null ? patch.countdownIntervals() : s.countdownIntervals(),
                            patch.startHorn() != null ? patch.startHorn() : s.startHorn(),
                            patch.finishHorn() != null ? patch.finishHorn() : s.finishHorn(),
                            patch.announceRaceCalls() != null ? patch.announceRaceCalls() : s.announceRaceCalls(),
                            patch.announceNextRace() != null ? patch.announceNextRace() : s.announceNextRace(),
                            patch.startCountdownSeconds() != null
                                    ? patch.startCountdownSeconds() : s.startCountdownSeconds(),
                            s.hornSounds(),  // changed only by uploading or removing a sound
                            patch.announceCarFinish() != null ? patch.announceCarFinish() : s.announceCarFinish(),
                            // Blank goes back to the standard wording
                            patch.timeUpText() != null ? patch.timeUpText() : s.timeUpText(),
                            patch.tickSound() != null ? patch.tickSound() : s.tickSound(),
                            patch.finalBeeps() != null ? patch.finalBeeps() : s.finalBeeps(),
                            patch.wording() != null ? patch.wording() : s.wording(),
                            patch.tuning() != null ? patch.tuning() : s.tuning()
                    ));
                });
        return ResponseEntity.ok(AudioSettingsDto.of(saved.getAudioSettings()));
    }

    /**
     * A short spoken sample from the club's Piper voice, for the test button (#286): the audio if Piper answered,
     * 503 if it is switched off or cannot be reached, so the official learns whether the race calls will be
     * spoken by Piper or fall back to the browser voice.
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
