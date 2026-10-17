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
 * Each subsequent lap interval is {@code intervalMs ± rand(0, jitterMs)} to simulate
 * realistic lap-time variation between drivers.
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

    private GenerativeMode() {}

    /**
     * Start emitting RC-4 frames. Blocks until the thread is interrupted or the stream closes.
     *
     * @param out          client output stream
     * @param transponders list of transponder ID strings to simulate
     * @param intervalMs   base milliseconds per lap (target lap time)
     * @param jitterMs     maximum random deviation added to each lap interval;
     *                     each lap time is {@code intervalMs + rand(-jitterMs, +jitterMs)}
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
        long[] nextPassingMs = new long[count];
        for (int i = 0; i < count; i++) {
            long stagger = (long)(i * intervalMs / (double) count);
            nextPassingMs[i] = startMs + intervalMs + stagger;
        }

        log.info("[SIMULATOR] GenerativeMode: {} transponders, base {}ms, jitter ±{}ms",
                transponders.size(), intervalMs, jitterMs);

        while (!Thread.currentThread().isInterrupted()) {
            long nowMs  = System.currentTimeMillis();
            double elapsedSec = (nowMs - startMs) / 1000.0;

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
                    String passing = String.format("@\t20\t%d\t%s\t%.3f\t300\t130\t2\txDEAD",
                            seqNum++, transponders.get(i), elapsedSec);
                    Rc4Lines.write(out, passing);
                    log.debug("[SIMULATOR] PASSING emitted: {}", passing);

                    // Next lap: base interval + random jitter in [-jitterMs, +jitterMs]
                    long jitter = jitterMs > 0
                            ? (long)((rng.nextDouble() * 2.0 - 1.0) * jitterMs)
                            : 0L;
                    nextPassingMs[i] = nowMs + intervalMs + jitter;
                }
            }

            Rc4Lines.pause(250); // 250 ms tick — tight enough for 10 s laps, light on CPU
        }
    }
}
