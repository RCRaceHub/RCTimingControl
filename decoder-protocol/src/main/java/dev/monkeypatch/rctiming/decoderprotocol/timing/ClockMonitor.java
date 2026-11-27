package dev.monkeypatch.rctiming.decoderprotocol.timing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Watches a decoder's clock against the server's, from the passings alone (a status record carries no decoder time).
 *
 * <p>For each passing the monitor takes the <em>residual</em>: the server's time when the passing arrived minus the
 * decoder's own time stamped on it. Both clocks tick at the same rate if the decoder is healthy, so the residual is a
 * constant (the unknown offset between the clocks) plus the delay the passing met on the way. That gives three
 * readings, none of which needs the anchor in {@link EpochAnchor}:
 * <ul>
 *   <li><strong>Jitter</strong> and <strong>latency</strong>: how much the delay varies over the last
 *       {@value #WINDOW} passings, and how far the typical one sits above the quickest.</li>
 *   <li><strong>Drift</strong>: the quickest residuals of the first and of the latest {@value #EDGE} passings are the
 *       delay-free ones, so a change between them over the elapsed decoder time is the decoder's clock gaining or
 *       losing on the server's.</li>
 *   <li><strong>Restart</strong>: reported by the caller when the decoder's clock goes back (see
 *       {@link EpochAnchor#detectsRestart(double)}); the monitor starts learning again.</li>
 * </ul>
 *
 * <p>Thresholds are the constants below. The monitor is detection only: lap times come from differences between the
 * decoder's own timestamps and are unaffected. The status listener is told only when the status changes, from
 * whichever thread calls {@link #observe}; in the app that is the decoder's Netty thread. All methods are
 * synchronized.
 */
public class ClockMonitor {

    /** Passings the jitter and latency are taken over. */
    static final int WINDOW = 20;
    /** Passings at each end the drift is compared between. */
    static final int EDGE = 10;
    /** Passings needed before any judgement. */
    static final int MIN_PASSINGS = 10;
    /**
     * A spread of delays wider than this is JITTERY (milliseconds). Set from a real club decoder over a whole meeting,
     * where passings reach the computer between 0 and about 1.4 s after the quickest one and the spread over 20
     * passings never passed 1.5 s; a lower figure would warn all day on healthy hardware.
     */
    static final double JITTER_WARN_MS = 3000;
    /**
     * A move of the quickest residual by at least this much (milliseconds), and by at least a third of the jitter, so a
     * noisy link cannot fake a drift from two chance extremes ... (the same decoder's quickest residual wandered by
     * about 200 ms over a day with no drift)
     */
    static final double DRIFT_WARN_MS = 300;
    /** ... at a rate of at least this (milliseconds per minute) is DRIFTING. */
    static final double DRIFT_WARN_MS_PER_MINUTE = 5;
    /** Decoder time that must pass before a drift rate is worked out (microseconds). */
    static final long DRIFT_MIN_ELAPSED_MICROS = 300_000_000L;
    /** While a warning stands, the listener is told again after this many passings so its figures stay current. */
    static final int REFRESH_EVERY = 10;

    private final Deque<Long> recent = new ArrayDeque<>();
    private final long[] firstResiduals = new long[EDGE];
    private int passings;
    private long firstDecoderMicros;
    private long lastDecoderMicros;
    private int restarts;
    // Passings left that still read as a restart; counts down once learning starts again
    private int restartedFor;
    private int sinceNotified;
    private ClockHealth health = ClockHealth.initial();
    private final java.util.function.LongSupplier arrivalClock;
    private volatile Consumer<ClockHealth> listener = h -> { };

    /** A monitor that stamps arrivals itself from a monotonic clock, so a step in the server's wall clock is not mistaken for drift. */
    public ClockMonitor() {
        long epochMicros = ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());
        long nanoBase = System.nanoTime();
        this.arrivalClock = () -> epochMicros + (System.nanoTime() - nanoBase) / 1000L;
    }

    /** A monitor that takes arrival times from {@code arrivalClock} (UTC microseconds), for tests. */
    ClockMonitor(java.util.function.LongSupplier arrivalClock) {
        this.arrivalClock = arrivalClock;
    }

    /** Sets who is told when the status changes; replaces any earlier listener. */
    public void setListener(Consumer<ClockHealth> listener) {
        this.listener = listener == null ? h -> { } : listener;
    }

    /** The latest reading. */
    public synchronized ClockHealth health() {
        return health;
    }

    /** Takes one passing in, stamping its arrival now; see {@link #observe(long, long)}. */
    public ClockHealth observeNow(long decoderMicros) {
        return observe(decoderMicros, arrivalClock.getAsLong());
    }

    /**
     * Takes one passing in.
     *
     * @param decoderMicros the decoder's own time on the passing, microseconds since it powered on
     * @param arrivalMicros the server's clock when the passing arrived, UTC microseconds
     * @return the reading after this passing
     */
    public ClockHealth observe(long decoderMicros, long arrivalMicros) {
        ClockHealth before;
        ClockHealth after;
        synchronized (this) {
            before = health;
            long residual = arrivalMicros - decoderMicros;
            if (passings == 0) {
                firstDecoderMicros = decoderMicros;
            }
            if (passings < EDGE) {
                firstResiduals[passings] = residual;
            }
            lastDecoderMicros = decoderMicros;
            passings++;
            recent.addLast(residual);
            if (recent.size() > WINDOW) {
                recent.removeFirst();
            }
            if (restartedFor > 0) {
                restartedFor--;
            }
            after = judge();
            health = after;
        }
        if (after.status() != before.status() || stale(after)) {
            synchronized (this) {
                sinceNotified = 0;
            }
            listener.accept(after);
        }
        return after;
    }

    /** Whether a standing warning has gone long enough without being told again for its figures to be out of date. */
    private synchronized boolean stale(ClockHealth reading) {
        boolean warning = reading.status() != ClockHealth.Status.OK && reading.status() != ClockHealth.Status.LEARNING;
        return warning && ++sinceNotified >= REFRESH_EVERY;
    }

    /**
     * Forgets what was learnt without counting a restart, for when the connection to the decoder is made again: a
     * burst of passings the decoder held back would otherwise read as a fault.
     */
    public void reset() {
        ClockHealth after;
        synchronized (this) {
            passings = 0;
            recent.clear();
            restartedFor = 0;
            sinceNotified = 0;
            after = new ClockHealth(ClockHealth.Status.LEARNING, 0, 0, 0, 0, restarts);
            health = after;
        }
        listener.accept(after);
    }

    /**
     * The decoder's clock went back, so it has restarted: forget what was learnt and report RESTARTED until a few
     * passings from the new run have come in.
     */
    public void restarted() {
        ClockHealth after;
        boolean already;
        synchronized (this) {
            already = health.status() == ClockHealth.Status.RESTARTED;
            restarts++;
            passings = 0;
            recent.clear();
            restartedFor = MIN_PASSINGS;
            after = new ClockHealth(ClockHealth.Status.RESTARTED, 0, 0, 0, 0, restarts);
            health = after;
        }
        if (!already) {
            listener.accept(after);
        }
    }

    private ClockHealth judge() {
        if (restartedFor > 0) {
            return new ClockHealth(ClockHealth.Status.RESTARTED, passings, 0, 0, 0, restarts);
        }
        if (passings < MIN_PASSINGS) {
            return new ClockHealth(ClockHealth.Status.LEARNING, passings, 0, 0, 0, restarts);
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        double sum = 0;
        for (long r : recent) {
            min = Math.min(min, r);
            max = Math.max(max, r);
            sum += r;
        }
        double jitterMs = (max - min) / 1000.0;
        double latencyMs = (sum / recent.size() - min) / 1000.0;
        double change = offsetChangeMs();
        double minutes = (lastDecoderMicros - firstDecoderMicros) / 60_000_000.0;
        double driftPerMinute = change == 0 || minutes <= 0 ? 0 : change / minutes;

        ClockHealth.Status status = ClockHealth.Status.OK;
        if (Math.abs(driftPerMinute) >= DRIFT_WARN_MS_PER_MINUTE
                && Math.abs(change) >= Math.max(DRIFT_WARN_MS, jitterMs / 3)) {
            status = ClockHealth.Status.DRIFTING;
        } else if (jitterMs >= JITTER_WARN_MS) {
            status = ClockHealth.Status.JITTERY;
        }
        return new ClockHealth(status, passings, jitterMs, latencyMs, driftPerMinute, restarts);
    }

    /** Milliseconds the quickest recent residual has moved from the quickest of the first passings; 0 if too soon. */
    private double offsetChangeMs() {
        if (passings < WINDOW || lastDecoderMicros - firstDecoderMicros < DRIFT_MIN_ELAPSED_MICROS) {
            return 0;
        }
        long first = Long.MAX_VALUE;
        for (long r : firstResiduals) {
            first = Math.min(first, r);
        }
        long latest = Long.MAX_VALUE;
        int skip = recent.size() - EDGE;
        int i = 0;
        for (long r : recent) {
            if (i++ >= skip) {
                latest = Math.min(latest, r);
            }
        }
        return (latest - first) / 1000.0;
    }
}
