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

    public AudioSettingsController(ClubProfileService clubProfileService) {
        this.clubProfileService = clubProfileService;
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
            int startCountdownSeconds
    ) {
        static AudioSettingsDto of(ClubAudioSettings s) {
            return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                    s.announceFinish(), s.announceRunningOrder(), s.runningOrderDepth(), s.countdownIntervals(),
                    s.startHorn(), s.finishHorn(), s.announceRaceCalls(), s.announceNextRace(),
                    s.startCountdownSeconds());
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
            @Min(0) @Max(60) Integer startCountdownSeconds
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
                                    ? patch.startCountdownSeconds() : s.startCountdownSeconds()
                    ));
                });
        return ResponseEntity.ok(AudioSettingsDto.of(saved.getAudioSettings()));
    }
}
