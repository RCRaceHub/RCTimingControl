package dev.monkeypatch.rctiming.domain.club;

/**
 * The club's audio switches. {@code startHorn} and {@code finishHorn} play a horn at the start and the finish,
 * {@code announceRaceCalls} the spoken grid call, "go" and stop, resume and abandon lines, and
 * {@code announceNextRace} the next race and marshals call after a finish, and {@code announceCarFinish} the car
 * number and driver name as each car takes the flag. {@code timeUpText} is what is said when the clock reaches
 * zero. {@code startCountdownSeconds} is the lead-in between pressing Start and the race starting, 0 for none.
 *
 * <p>Settings saved before these were added have none of them, so a missing switch is on and a missing lead-in
 * is 10 seconds (0 means none). {@code hornSounds} holds the club's own start and finish sound files, none until
 * the club uploads one.
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
        Integer startCountdownSeconds,
        HornSounds hornSounds,
        Boolean announceCarFinish,
        String timeUpText,
        Integer runningOrderPercent,
        Integer runningOrderAllUpTo
) {
    /** The lead-in for settings saved before it was added. */
    public static final int DEFAULT_START_COUNTDOWN_SECONDS = 10;
    /** The share of the field the running order names, until the club sets its own. */
    public static final int DEFAULT_RUNNING_ORDER_PERCENT = 50;
    /** A race with this many drivers or fewer has them all named, until the club sets its own. */
    public static final int DEFAULT_RUNNING_ORDER_ALL_UP_TO = 5;
    /** What is said when a timed race's clock reaches zero, until the club words its own. */
    public static final String DEFAULT_TIME_UP_TEXT = "Time. Cars finish as they cross the line.";

    /**
     * The club's own horn sound files: where each is served from and how long it plays, in milliseconds, so a
     * spoken line can follow it. A horn with no file uses the built-in one.
     */
    public record HornSounds(String startUrl, Integer startMs, String finishUrl, Integer finishMs) {
        public static final HornSounds NONE = new HornSounds(null, null, null, null);

        public HornSounds withStart(String url, Integer ms) {
            return new HornSounds(url, ms, finishUrl, finishMs);
        }

        public HornSounds withFinish(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, url, ms);
        }
    }

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
        startCountdownSeconds = startCountdownSeconds == null
                ? DEFAULT_START_COUNTDOWN_SECONDS : startCountdownSeconds;
        hornSounds = hornSounds == null ? HornSounds.NONE : hornSounds;
        announceCarFinish = announceCarFinish == null || announceCarFinish;
        timeUpText = timeUpText == null || timeUpText.isBlank() ? DEFAULT_TIME_UP_TEXT : timeUpText.strip();
        runningOrderPercent = runningOrderPercent == null ? DEFAULT_RUNNING_ORDER_PERCENT : runningOrderPercent;
        runningOrderAllUpTo = runningOrderAllUpTo == null ? DEFAULT_RUNNING_ORDER_ALL_UP_TO : runningOrderAllUpTo;
    }

    /** The settings from before the running order named a share of the field, which take their defaults. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals, Boolean startHorn, Boolean finishHorn,
                             Boolean announceRaceCalls, Boolean announceNextRace, Integer startCountdownSeconds,
                             HornSounds hornSounds, Boolean announceCarFinish, String timeUpText) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, startHorn, finishHorn, announceRaceCalls, announceNextRace,
                startCountdownSeconds, hornSounds, announceCarFinish, timeUpText, null, null);
    }

    /**
     * How many drivers a running-order call names for a field of this size: all of them when the field is
     * {@code runningOrderAllUpTo} or fewer, otherwise {@code runningOrderPercent} of it rounded up.
     */
    public int runningOrderCount(int drivers) {
        if (drivers <= runningOrderAllUpTo) {
            return drivers;
        }
        return Math.min(drivers, Math.max(1, (drivers * runningOrderPercent + 99) / 100));
    }

    /** The settings from before the finish calls and the time call wording, which take their defaults. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals, Boolean startHorn, Boolean finishHorn,
                             Boolean announceRaceCalls, Boolean announceNextRace, Integer startCountdownSeconds,
                             HornSounds hornSounds) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, startHorn, finishHorn, announceRaceCalls, announceNextRace,
                startCountdownSeconds, hornSounds, null, null);
    }

    /** The settings from before the club's own horn sounds, which have none. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals, Boolean startHorn, Boolean finishHorn,
                             Boolean announceRaceCalls, Boolean announceNextRace, Integer startCountdownSeconds) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, startHorn, finishHorn, announceRaceCalls, announceNextRace,
                startCountdownSeconds, null);
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
