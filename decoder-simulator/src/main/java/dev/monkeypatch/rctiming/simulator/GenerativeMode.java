package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;

/**
 * Emits synthetic RC-4 PASSING records for a configured list of transponder IDs.
 *
 * <p>Each transponder has its own independent lap timer. Initial passings are staggered
 * evenly across one base interval so all drivers don't cross the line simultaneously.
 * Lap times are varied the way real ones are: each driver gets a fixed pace of up to
 * {@value #MAX_PACE_SPREAD} faster or slower than the base interval, each lap adds bell-curve noise (standard
 * deviation a third of {@code jitterMs}) down to the millisecond, and about one lap in
 * {@value #SLOW_LAP_ONE_IN} is a slow lap (a spin or a marshal stop). Passings are stamped with their scheduled
 * crossing time rather than the 20 ms tick that sends them, so lap times are not quantised.
 *
 * <p>A STATUS (heartbeat) frame is emitted every 5 seconds. Sequence numbers are
 * monotonically increasing across both PASSING and STATUS records, matching real
 * decoder behaviour.
 *
 * <p>The {@code timeSinceStart} field of each PASSING is the seconds since this client's session began (standing in
 * for decoder power-on), so the app's epoch anchoring sees a clock that starts near zero for every connection. Every
 * record uses decoder id 20, 300 hits, strength 130 and the dummy CRC {@code xDEAD}; the app's parser does not check
 * the CRC. Each connected client gets its own run of this mode, with its own sequence numbers.
 */
public class GenerativeMode {

    private static final Logger log = LoggerFactory.getLogger(GenerativeMode.class);

    /** Largest fixed pace offset a driver gets, as a fraction of the base interval (either way). */
    static final String MAX_PACE_SPREAD = "5%";
    private static final double PACE_SPREAD = 0.05;
    /** About one lap in this many is a slow lap. */
    static final int SLOW_LAP_ONE_IN = 15;

    private GenerativeMode() {}

    /**
     * Gives each driver a fixed pace factor so some are consistently quicker than others.
     *
     * @return one factor per driver, uniformly in {@code [1 - 5%, 1 + 5%]}
     */
    static double[] paceFactors(int count, Random rng) {
        double[] pace = new double[count];
        for (int i = 0; i < count; i++) {
            pace[i] = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * PACE_SPREAD;
        }
        return pace;
    }

    /**
     * Picks the next lap time for one driver.
     *
     * @param intervalMs base lap time, ms
     * @param pace       the driver's fixed pace factor from {@link #paceFactors}
     * @param jitterMs   spread of the per-lap noise, ms; zero gives the driver's pace exactly
     * @return the lap time in milliseconds, fractional, never below half the base interval
     */
    static double nextLapMs(long intervalMs, double pace, long jitterMs, Random rng) {
        double lap = intervalMs * pace;
        if (jitterMs > 0) {
            double noise = rng.nextGaussian() * jitterMs / 3.0;
            lap += Math.max(-jitterMs, Math.min(jitterMs, noise));
            if (rng.nextInt(SLOW_LAP_ONE_IN) == 0) {
                lap += jitterMs * (0.5 + rng.nextDouble() * 2.0);
            }
        }
        return Math.max(intervalMs / 2.0, lap);
    }

    /**
     * Start emitting RC-4 frames. Blocks until the thread is interrupted or the stream closes.
     *
     * @param out          client output stream
     * @param transponders list of transponder ID strings to simulate
     * @param intervalMs   base milliseconds per lap (target lap time)
     * @param jitterMs     spread of the per-lap noise (clamped to ±{@code jitterMs}), and the scale of slow laps
     *                     (which add up to 2.5 × {@code jitterMs}); zero gives steady laps at each driver's pace
     * @throws IOException if the stream write fails
     */
    public static void run(OutputStream out, List<String> transponders,
                           long intervalMs, long jitterMs) throws IOException {
        if (transponders.isEmpty()) {
            log.warn("[SIMULATOR] GenerativeMode started with no transponders — emitting STATUS only");
        }

        Random rng       = new Random();
        long   startMs   = System.currentTimeMillis();
        long   seqNum    = 0;
        long   lastStatusMs = startMs;

        // Schedule each transponder's first passing, staggered evenly across one base interval
        // so drivers don't all cross the line at the same moment.
        int    count        = Math.max(1, transponders.size());
        double[] pace = paceFactors(count, rng);
        double[] nextPassingMs = new double[count];
        for (int i = 0; i < count; i++) {
            double stagger = i * intervalMs / (double) count;
            nextPassingMs[i] = startMs + intervalMs * pace[i] + stagger;
        }

        log.info("[SIMULATOR] GenerativeMode: {} transponders, base {}ms, jitter ±{}ms, pace {}",
                transponders.size(), intervalMs, jitterMs, java.util.Arrays.toString(pace));

        while (!Thread.currentThread().isInterrupted()) {
            long nowMs  = System.currentTimeMillis();

            // Emit STATUS heartbeat every 5 s
            if (nowMs - lastStatusMs >= 5_000) {
                String status = String.format("#\t20\t%d\t72\t0\txDEAD", seqNum++);
                Rc4Lines.write(out, status);
                lastStatusMs = nowMs;
                log.debug("[SIMULATOR] STATUS emitted: {}", status);
            }

            // Check each transponder independently
            for (int i = 0; i < transponders.size(); i++) {
                if (nowMs >= nextPassingMs[i]) {
                    double elapsedSec = (nextPassingMs[i] - startMs) / 1000.0;
                    String passing = String.format("@\t20\t%d\t%s\t%.3f\t300\t130\t2\txDEAD",
                            seqNum++, transponders.get(i), elapsedSec);
                    Rc4Lines.write(out, passing);
                    log.debug("[SIMULATOR] PASSING emitted: {}", passing);

                    // Next lap is scheduled from this crossing, not from when the tick sent it
                    nextPassingMs[i] += nextLapMs(intervalMs, pace[i], jitterMs, rng);
                }
            }

            Rc4Lines.pause(20); // 20 ms tick — passings are stamped with their scheduled time, the tick only sends them
        }
    }
}
