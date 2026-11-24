package dev.monkeypatch.rctiming.api.racecontrol.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;

/**
 * The club's announcer settings as the API answers them, for {@code GET} and {@code PATCH} on
 * {@code /api/v1/race-control/settings/audio} and for an audio template's settings. It mirrors
 * {@link ClubAudioSettings}, which says what each switch does; {@code startCountdownSeconds} is 0 for no lead-in
 * and {@code countdownIntervals} are in seconds. {@code hornSounds} is read only here: the club's own sound files
 * change by uploading or removing them under {@code /api/v1/admin/audio/horns}.
 *
 * <p>{@code defaultVoiceId} is the club's default Piper voice: an empty string until one is chosen. It is left out
 * of a template's settings, since a template never holds the voice.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AudioSettingsDto(
        boolean announceCountdown,
        boolean announceStagger,
        boolean announceLapBeep,
        boolean announceFinish,
        boolean announceRunningOrder,
        int runningOrderPercent,
        int runningOrderAllUpTo,
        String defaultVoiceId,
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
        ClubAudioSettings.Tuning tuning,
        boolean paceBeeps
) {
    /** The settings with the club's default voice ({@code ""} when none is chosen). */
    public static AudioSettingsDto of(ClubAudioSettings s, String defaultVoiceId) {
        return new AudioSettingsDto(s.announceCountdown(), s.announceStagger(), s.announceLapBeep(),
                s.announceFinish(), s.announceRunningOrder(), s.runningOrderPercent(), s.runningOrderAllUpTo(),
                defaultVoiceId, s.countdownIntervals(),
                s.startHorn(), s.finishHorn(), s.announceRaceCalls(), s.announceNextRace(),
                s.startCountdownSeconds(), s.hornSounds(), s.announceCarFinish(), s.timeUpText(), s.tickSound(),
                s.finalBeeps(), s.wording(), s.tuning(), s.paceBeeps());
    }

    /** The settings alone, as a template holds them (no voice). */
    public static AudioSettingsDto of(ClubAudioSettings s) {
        return of(s, null);
    }
}
