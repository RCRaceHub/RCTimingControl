package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * One fake decoder at one loop of a {@link TrackSimulation} (#678): the start/finish loop or a split.
 *
 * <p>Like {@link GenerativeMode}, the {@code timeSinceStart} of each PASSING is seconds since this client connected,
 * standing in for decoder power-on, and a STATUS record goes out every 5 seconds. Only crossings from the moment the
 * client connected are sent. {@code delayMs} holds every record back that long before it is sent, which is how the
 * simulator fakes a decoder whose clock the app anchors late: the app anchors each decoder to the server's arrival
 * time of its first passing, so a delay shifts all of that decoder's times against the others. The record itself keeps
 * the true time, as a real decoder's would.
 */
final class TrackMode {

    private static final Logger log = LoggerFactory.getLogger(TrackMode.class);

    private TrackMode() {
    }

    /**
     * Sends this loop's crossings to one client until the thread is interrupted or the stream fails.
     *
     * @param out     the client's output stream
     * @param track   the shared track
     * @param loop    0 for the start/finish loop, 1 for the first split, and so on
     * @param delayMs how long each record is held back before sending, ms
     * @throws IOException if the stream write fails
     */
    static void run(OutputStream out, TrackSimulation track, int loop, long delayMs) throws IOException {
        long connectMs = System.currentTimeMillis();
        long seqNum = 0;
        long lastStatusMs = connectMs;
        // Everything before this has been looked at; what is due is [sentUpTo, now - delay)
        double sentUpTo = connectMs;
        log.info("[SIMULATOR] TrackMode: loop {} of {}, delay {} ms", loop, track.loopCount() - 1, delayMs);

        while (!Thread.currentThread().isInterrupted()) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastStatusMs >= 5_000) {
                Rc4Lines.write(out, String.format(Locale.ROOT, "#\t20\t%d\t72\t0\txDEAD", seqNum++));
                lastStatusMs = nowMs;
            }
            double due = nowMs - delayMs;
            for (TrackSimulation.Crossing crossing : track.crossings(loop, sentUpTo, due)) {
                double elapsedSec = (crossing.timeMs() - connectMs) / 1000.0;
                Rc4Lines.write(out, String.format(Locale.ROOT, "@\t%d\t%d\t%s\t%.3f\t300\t130\t2\txDEAD",
                        20 + loop, seqNum++, crossing.transponder(), elapsedSec));
            }
            sentUpTo = Math.max(sentUpTo, due);
            Rc4Lines.pause(20);
        }
    }
}
