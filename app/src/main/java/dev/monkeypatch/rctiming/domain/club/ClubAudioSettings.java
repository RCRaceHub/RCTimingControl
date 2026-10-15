package dev.monkeypatch.rctiming.domain.club;

/**
 * The club's audio switches. {@code startHorn} and {@code finishHorn} play a horn at the start and the finish,
 * {@code announceRaceCalls} the spoken grid call, "go" and stop, resume and abandon lines, and
 * {@code announceNextRace} the next race and marshals call after a finish. {@code startCountdownSeconds} is the
 * lead-in between pressing Start and the race starting, 0 for none.
 *
 * <p>Settings saved before these were added have none of them, so a missing switch is on and a missing lead-in
 * is none.
 */
public record ClubAudioSettings(
        boolean announceCountdown,
        boolean announceStagger,
        boolean announceLapBeep,
        boolean announceFinish,
        boolean announceRunningOrder,
        int runningOrderDepth,
        int[] countdownIntervals,
        Boolean startHorn,
        Boolean finishHorn,
        Boolean announceRaceCalls,
        Boolean announceNextRace,
        Integer startCountdownSeconds
) {
    /**
     * Compact constructor — ensures countdownIntervals is never null.
     * Default sequence: 10 min, 5 min, 2 min, 1 min, 30 sec (in seconds).
     */
    public ClubAudioSettings {
        if (countdownIntervals == null) {
            countdownIntervals = new int[]{600, 300, 120, 60, 30};
        }
        startHorn = startHorn == null || startHorn;
        finishHorn = finishHorn == null || finishHorn;
        announceRaceCalls = announceRaceCalls == null || announceRaceCalls;
        announceNextRace = announceNextRace == null || announceNextRace;
        startCountdownSeconds = startCountdownSeconds == null ? 0 : startCountdownSeconds;
    }

    /** The settings from before the horns and race calls, which take their defaults. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, null, null, null, null, null);
    }

    public static ClubAudioSettings defaults() {
        return new ClubAudioSettings(true, true, true, true, true, 3, new int[]{600, 300, 120, 60, 30});
    }
}
