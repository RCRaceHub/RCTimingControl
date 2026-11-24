package dev.monkeypatch.rctiming.api.racecontrol.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A change to the club's announcer settings: the body of {@code PATCH /api/v1/race-control/settings/audio}, sent
 * by race control's panel and by the admin audio page. A field left out (null) keeps its saved value, so a client
 * can send just the toggle it changed. {@code wording} and {@code tuning} are replaced as a whole when sent, so a
 * client sends the full object (a line left empty has the standard wording). {@code hornSounds} is not here: the
 * sound files change only by upload or removal. A null or blank {@code defaultVoiceId} keeps the stored voice.
 *
 * <p>The limits are checked on the way in and a breach is a 400: {@code runningOrderPercent} 1 to 100,
 * {@code runningOrderAllUpTo} 0 to 100, {@code startCountdownSeconds} 0 to 60, {@code countdownIntervals} at most
 * 10 times of 1 to 3600 seconds each, {@code timeUpText} at most 200 characters.
 */
public record AudioSettingsPatch(
        Boolean announceCountdown,
        Boolean announceStagger,
        Boolean announceLapBeep,
        Boolean announceFinish,
        Boolean announceRunningOrder,
        @Min(1) @Max(100) Integer runningOrderPercent,
        @Min(0) @Max(100) Integer runningOrderAllUpTo,
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
        @Valid ClubAudioSettings.Tuning tuning,
        Boolean paceBeeps,
        String defaultVoiceId
) {
    /** Each interval is a clip the server makes, so each must be from a second up to an hour. */
    @JsonIgnore
    @AssertTrue(message = "countdownIntervals must each be from 1 to 3600 seconds")
    public boolean isCountdownIntervalsInRange() {
        return ClubAudioSettings.intervalsInRange(countdownIntervals);
    }

    /** True when the patch names a voice to store. */
    public boolean hasVoice() {
        return defaultVoiceId != null && !defaultVoiceId.isBlank();
    }

    /** The saved settings with every field this patch names replaced. */
    public ClubAudioSettings applyTo(ClubAudioSettings s) {
        return new ClubAudioSettings(
                announceCountdown != null ? announceCountdown : s.announceCountdown(),
                announceStagger != null ? announceStagger : s.announceStagger(),
                announceLapBeep != null ? announceLapBeep : s.announceLapBeep(),
                announceFinish != null ? announceFinish : s.announceFinish(),
                announceRunningOrder != null ? announceRunningOrder : s.announceRunningOrder(),
                s.runningOrderDepth(),  // no longer used, kept as saved
                countdownIntervals != null ? countdownIntervals : s.countdownIntervals(),
                startHorn != null ? startHorn : s.startHorn(),
                finishHorn != null ? finishHorn : s.finishHorn(),
                announceRaceCalls != null ? announceRaceCalls : s.announceRaceCalls(),
                announceNextRace != null ? announceNextRace : s.announceNextRace(),
                startCountdownSeconds != null ? startCountdownSeconds : s.startCountdownSeconds(),
                s.hornSounds(),  // changed only by uploading or removing a sound
                announceCarFinish != null ? announceCarFinish : s.announceCarFinish(),
                // Blank goes back to the standard wording
                timeUpText != null ? timeUpText : s.timeUpText(),
                tickSound != null ? tickSound : s.tickSound(),
                finalBeeps != null ? finalBeeps : s.finalBeeps(),
                wording != null ? wording : s.wording(),
                tuning != null ? tuning : s.tuning(),
                runningOrderPercent != null ? runningOrderPercent : s.runningOrderPercent(),
                runningOrderAllUpTo != null ? runningOrderAllUpTo : s.runningOrderAllUpTo(),
                paceBeeps != null ? paceBeeps : s.paceBeeps());
    }
}
