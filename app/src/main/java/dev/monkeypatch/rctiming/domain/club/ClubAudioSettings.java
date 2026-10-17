package dev.monkeypatch.rctiming.domain.club;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * The club's audio switches. {@code startHorn} and {@code finishHorn} play a horn at the start and the finish,
 * {@code announceRaceCalls} the spoken grid call, "go" and stop, resume and abandon lines, and
 * {@code announceNextRace} the next race and marshals call after a finish, and {@code announceCarFinish} the car
 * number and driver name as each car takes the flag. {@code timeUpText} is what is said when the clock reaches
 * zero. {@code startCountdownSeconds} is the lead-in between pressing Start and the race starting, 0 for none.
 *
 * <p>Settings saved before these were added have none of them, so a missing switch is on and a missing lead-in
 * is 10 seconds (0 means none). {@code hornSounds} holds the club's own start and finish sound files, none until
 * the club uploads one. {@code tickSound} and {@code finalBeeps} are the start lead-in's ticks and the beeps in the
 * last seconds of a timed race, each with its own switch (the lap beep has {@code announceLapBeep}). Unlike the
 * other switches, a missing {@code finalBeeps} is off: the end of a race is not counted down unless the club asks.
 * {@code wording} is how each spoken line is worded and {@code tuning} the running order cadence, the
 * final-seconds count and the lap beep's tone; a blank wording or a missing number takes the standard.
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
        Boolean tickSound,
        Boolean finalBeeps,
        Wording wording,
        Tuning tuning,
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
     * The club's own sound files: where each is served from and how long it plays, in milliseconds, so a spoken
     * line can follow a horn. A sound with no file uses the built-in one.
     */
    public record HornSounds(String startUrl, Integer startMs, String finishUrl, Integer finishMs,
                             String tickUrl, Integer tickMs, String lapBeepUrl, Integer lapBeepMs,
                             String fasterBeepUrl, Integer fasterBeepMs, String slowerBeepUrl,
                             Integer slowerBeepMs) {
        public static final HornSounds NONE = new HornSounds(null, null, null, null, null, null, null, null,
                null, null, null, null);

        /** The sounds from before the tick and the lap beep could be replaced. */
        public HornSounds(String startUrl, Integer startMs, String finishUrl, Integer finishMs) {
            this(startUrl, startMs, finishUrl, finishMs, null, null, null, null, null, null, null, null);
        }

        /** The sounds from before the faster and slower lap beeps could be replaced. */
        public HornSounds(String startUrl, Integer startMs, String finishUrl, Integer finishMs,
                          String tickUrl, Integer tickMs, String lapBeepUrl, Integer lapBeepMs) {
            this(startUrl, startMs, finishUrl, finishMs, tickUrl, tickMs, lapBeepUrl, lapBeepMs,
                    null, null, null, null);
        }

        public HornSounds withStart(String url, Integer ms) {
            return new HornSounds(url, ms, finishUrl, finishMs, tickUrl, tickMs, lapBeepUrl, lapBeepMs,
                    fasterBeepUrl, fasterBeepMs, slowerBeepUrl, slowerBeepMs);
        }

        public HornSounds withFinish(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, url, ms, tickUrl, tickMs, lapBeepUrl, lapBeepMs,
                    fasterBeepUrl, fasterBeepMs, slowerBeepUrl, slowerBeepMs);
        }

        public HornSounds withTick(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, finishUrl, finishMs, url, ms, lapBeepUrl, lapBeepMs,
                    fasterBeepUrl, fasterBeepMs, slowerBeepUrl, slowerBeepMs);
        }

        /** The beep for a lap on pace with the driver's best. */
        public HornSounds withLapBeep(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, finishUrl, finishMs, tickUrl, tickMs, url, ms,
                    fasterBeepUrl, fasterBeepMs, slowerBeepUrl, slowerBeepMs);
        }

        /** The beep for a lap faster than the driver's best. */
        public HornSounds withFasterBeep(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, finishUrl, finishMs, tickUrl, tickMs, lapBeepUrl, lapBeepMs,
                    url, ms, slowerBeepUrl, slowerBeepMs);
        }

        /** The beep for a lap slower than the driver's best. */
        public HornSounds withSlowerBeep(String url, Integer ms) {
            return new HornSounds(startUrl, startMs, finishUrl, finishMs, tickUrl, tickMs, lapBeepUrl, lapBeepMs,
                    fasterBeepUrl, fasterBeepMs, url, ms);
        }
    }

    /**
     * How the club words each spoken line. {@code {seconds}}, {@code {race}}, {@code {drivers}},
     * {@code {marshals}}, {@code {positions}}, {@code {car}} and {@code {name}} stand for what changes from
     * call to call. A blank line takes the standard wording.
     */
    public record Wording(@Size(max = 200) String startLeadIn, @Size(max = 200) String gridCall,
                          @Size(max = 200) String go, @Size(max = 200) String stop,
                          @Size(max = 200) String resume, @Size(max = 200) String abandon,
                          @Size(max = 200) String raceFinished, @Size(max = 200) String nextRace,
                          @Size(max = 200) String marshals, @Size(max = 200) String runningOrder,
                          @Size(max = 200) String carFinish) {
        public static final String DEFAULT_START_LEAD_IN = "Starting in {seconds} seconds.";
        public static final String DEFAULT_GRID_CALL = "{race}. {drivers} to the grid.";
        public static final String DEFAULT_GO = "Race started.";
        public static final String DEFAULT_STOP = "Race stopped.";
        public static final String DEFAULT_RESUME = "Race resumed.";
        public static final String DEFAULT_ABANDON = "Race abandoned.";
        public static final String DEFAULT_RACE_FINISHED = "Race finished. Checkered flag.";
        public static final String DEFAULT_NEXT_RACE = "Next race: {race}.";
        public static final String DEFAULT_MARSHALS = "Marshals: {marshals}.";
        public static final String DEFAULT_RUNNING_ORDER = "Running order: {positions}.";
        /** Said for a car with a number; a car with none is called by name alone. */
        public static final String DEFAULT_CAR_FINISH = "Car {car}, {name}.";

        public Wording {
            startLeadIn = orDefault(startLeadIn, DEFAULT_START_LEAD_IN);
            gridCall = orDefault(gridCall, DEFAULT_GRID_CALL);
            go = orDefault(go, DEFAULT_GO);
            stop = orDefault(stop, DEFAULT_STOP);
            resume = orDefault(resume, DEFAULT_RESUME);
            abandon = orDefault(abandon, DEFAULT_ABANDON);
            raceFinished = orDefault(raceFinished, DEFAULT_RACE_FINISHED);
            nextRace = orDefault(nextRace, DEFAULT_NEXT_RACE);
            marshals = orDefault(marshals, DEFAULT_MARSHALS);
            runningOrder = orDefault(runningOrder, DEFAULT_RUNNING_ORDER);
            carFinish = orDefault(carFinish, DEFAULT_CAR_FINISH);
        }

        public static final Wording STANDARD = new Wording(null, null, null, null, null, null, null, null, null,
                null, null);

        private static String orDefault(String text, String standard) {
            return text == null || text.isBlank() ? standard : text.strip();
        }

        /** The line with each {@code {key}} replaced by its value in {@code values} (pairs of key and value). */
        public static String fill(String template, String... values) {
            java.util.Map<String, String> byKey = new java.util.HashMap<>();
            for (int i = 0; i + 1 < values.length; i += 2) {
                byKey.put(values[i], values[i + 1]);
            }
            // One pass, so a value that holds a later {key} is not filled in again
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\w+)}").matcher(template);
            return matcher.replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                    byKey.getOrDefault(m.group(1), m.group())));
        }
    }

    /**
     * Timings and tones: how often the running order is called (every {@code runningOrderEarlyEveryMinutes} for
     * the first {@code runningOrderEarlyPeriodMinutes} of a race, then every {@code runningOrderLaterEveryMinutes}),
     * how many of the last seconds get a count, and the lap beep's pitch (the beep for an improved lap is an
     * octave higher) and length.
     */
    public record Tuning(@Min(0) @Max(120) Integer runningOrderEarlyPeriodMinutes,
                         @Min(1) @Max(60) Integer runningOrderEarlyEveryMinutes,
                         @Min(1) @Max(60) Integer runningOrderLaterEveryMinutes,
                         @Min(0) @Max(10) Integer finalSeconds, @Min(100) @Max(2000) Integer lapBeepHz,
                         @Min(50) @Max(1000) Integer lapBeepMs) {
        public static final Tuning STANDARD = new Tuning(null, null, null, null, null, null);

        public Tuning {
            runningOrderEarlyPeriodMinutes = runningOrderEarlyPeriodMinutes == null ? 10
                    : runningOrderEarlyPeriodMinutes;
            runningOrderEarlyEveryMinutes = runningOrderEarlyEveryMinutes == null ? 2
                    : runningOrderEarlyEveryMinutes;
            runningOrderLaterEveryMinutes = runningOrderLaterEveryMinutes == null ? 5
                    : runningOrderLaterEveryMinutes;
            finalSeconds = finalSeconds == null ? 5 : finalSeconds;
            lapBeepHz = lapBeepHz == null ? 440 : lapBeepHz;
            lapBeepMs = lapBeepMs == null ? 200 : lapBeepMs;
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
        tickSound = tickSound == null || tickSound;
        finalBeeps = finalBeeps != null && finalBeeps;
        wording = wording == null ? Wording.STANDARD : wording;
        tuning = tuning == null ? Tuning.STANDARD : tuning;
        runningOrderPercent = runningOrderPercent == null ? DEFAULT_RUNNING_ORDER_PERCENT : runningOrderPercent;
        runningOrderAllUpTo = runningOrderAllUpTo == null ? DEFAULT_RUNNING_ORDER_ALL_UP_TO : runningOrderAllUpTo;
    }

    /** The settings from before the running order named a share of the field, which take their defaults. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals, Boolean startHorn, Boolean finishHorn,
                             Boolean announceRaceCalls, Boolean announceNextRace, Integer startCountdownSeconds,
                             HornSounds hornSounds, Boolean announceCarFinish, String timeUpText,
                             Boolean tickSound, Boolean finalBeeps, Wording wording, Tuning tuning) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, startHorn, finishHorn, announceRaceCalls, announceNextRace,
                startCountdownSeconds, hornSounds, announceCarFinish, timeUpText, tickSound, finalBeeps, wording,
                tuning, null, null);
    }

    /** The settings from before the wording, timings and tones could be changed, which take their defaults. */
    public ClubAudioSettings(boolean announceCountdown, boolean announceStagger, boolean announceLapBeep,
                             boolean announceFinish, boolean announceRunningOrder, int runningOrderDepth,
                             int[] countdownIntervals, Boolean startHorn, Boolean finishHorn,
                             Boolean announceRaceCalls, Boolean announceNextRace, Integer startCountdownSeconds,
                             HornSounds hornSounds, Boolean announceCarFinish, String timeUpText) {
        this(announceCountdown, announceStagger, announceLapBeep, announceFinish, announceRunningOrder,
                runningOrderDepth, countdownIntervals, startHorn, finishHorn, announceRaceCalls, announceNextRace,
                startCountdownSeconds, hornSounds, announceCarFinish, timeUpText, null, null, null, null);
    }

    /** Each countdown call is a clip the server makes, so each time must be from a second up to an hour. */
    public static boolean intervalsInRange(int[] intervals) {
        return intervals == null || java.util.Arrays.stream(intervals).allMatch(i -> i >= 1 && i <= 3600);
    }

    /** Whether the clips made for a race would come out differently under {@code other}. */
    public boolean clipsDifferFrom(ClubAudioSettings other) {
        return !java.util.Arrays.equals(countdownIntervals, other.countdownIntervals)
                || !wording.carFinish().equals(other.wording.carFinish())
                || !wording.raceFinished().equals(other.wording.raceFinished())
                || !announceCarFinish.equals(other.announceCarFinish);
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

    public ClubAudioSettings withHornSounds(HornSounds sounds) {
        return new ClubAudioSettings(announceCountdown, announceStagger, announceLapBeep, announceFinish,
                announceRunningOrder, runningOrderDepth, countdownIntervals, startHorn, finishHorn,
                announceRaceCalls, announceNextRace, startCountdownSeconds, sounds, announceCarFinish, timeUpText,
                tickSound, finalBeeps, wording, tuning, runningOrderPercent, runningOrderAllUpTo);
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

    /** Every switch on and the standard timings: used before a club profile exists or has saved any. */
    public static ClubAudioSettings defaults() {
        return new ClubAudioSettings(true, true, true, true, true, 3, new int[]{600, 300, 120, 60, 30});
    }
}
