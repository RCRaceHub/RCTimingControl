package dev.monkeypatch.rctiming.decoderprotocol.timing;

import java.time.Instant;

/**
 * Converts RC-4 {@code timeSinceStart_s} (seconds since decoder power-on) to
 * absolute wall-clock UTC microseconds by anchoring to the server clock, since the protocol
 * carries no absolute timestamp.
 *
 * <h3>Formula</h3>
 * <pre>
 * rtcTimeMicros = cut(anchor) + cut(timeSinceStart) - cut(firstTimeSinceStart)
 * </pre>
 * where {@code anchor} is {@code Instant.now()} (whole milliseconds) captured at the FIRST PASSING record and
 * {@code cut} rounds down to the truncation step (1 ms by default, 10 ms for hundredths of a second, see
 * {@link #setTruncationMicros(long)}).
 *
 * <h3>Decoder restart detection</h3>
 * If {@code timeSinceStart_s} regresses by more than 10 seconds from the last seen value,
 * the decoder is assumed to have restarted. The epoch is reset so the next call re-anchors.
 *
 * <p>The decoder's time is read exactly, to the microsecond, from the digits it sent. The anchor is the server's
 * arrival time of the first passing, so every time is that much later than the true crossing; differences between
 * passings are exactly the differences between the decoder's own times, so the anchor and the network delay never
 * reach a lap time.
 *
 * <p>All methods are synchronized for thread safety (called from a single Netty I/O thread
 * in normal operation, but made safe for testing).
 */
public class EpochAnchor {

    private volatile long truncationMicros = 1_000L;
    private Instant decoderEpoch;
    private long    firstMicros;
    private Double  lastTimeSinceStart;

    /**
     * Sets the step the decoder's time is cut to before it is anchored: 1000 (whole milliseconds, the decoder's own
     * resolution and the default) or 10000 (hundredths of a second, truncated, never rounded). The cut is made on
     * the decoder's own time, so every passing time from here is a whole multiple of the step and so is every lap
     * time, total and gap worked out from them. Changing it while a race runs can move later times by less than one
     * step, so change it between races.
     *
     * @param micros the step in microseconds, at least 1
     */
    public void setTruncationMicros(long micros) {
        this.truncationMicros = Math.max(1L, micros);
    }

    /**
     * Convert {@code timeSinceStart} to epoch-anchored UTC microseconds. For callers that hold a double; the decoder
     * listener uses {@link #toRtcTimeMicrosExact(long)}.
     *
     * @param timeSinceStart seconds since decoder power-on
     * @return UTC timestamp in microseconds
     */
    public long toRtcTimeMicros(double timeSinceStart) {
        return toRtcTimeMicrosExact(Math.round(timeSinceStart * 1_000_000d));
    }

    /**
     * Convert the decoder's time to epoch-anchored UTC microseconds. On the very first call the epoch is anchored to
     * {@code Instant.now()} in whole milliseconds. The decoder's time and the anchor are both cut to the truncation
     * step, so the difference between two results is exactly the difference between the decoder's two cut times.
     *
     * @param timeSinceStartMicros microseconds since decoder power-on, exact
     * @return UTC timestamp in microseconds, a whole multiple of the truncation step
     */
    public synchronized long toRtcTimeMicrosExact(long timeSinceStartMicros) {
        if (decoderEpoch == null) {
            // First PASSING — anchor epoch now
            decoderEpoch = Instant.now();
            firstMicros  = timeSinceStartMicros;
        }
        lastTimeSinceStart = timeSinceStartMicros / 1_000_000d;
        long step = truncationMicros;
        long anchorMicros = decoderEpoch.toEpochMilli() * 1000L;
        return cut(anchorMicros, step) + (cut(timeSinceStartMicros, step) - cut(firstMicros, step));
    }

    private static long cut(long micros, long step) {
        return micros - Math.floorMod(micros, step);
    }

    /**
     * Check whether {@code timeSinceStart} indicates a decoder restart
     * (regression of more than 10 s below the last observed value).
     *
     * <p>If a restart is detected the epoch is reset; the next call to
     * {@link #toRtcTimeMicrosExact(long)} will re-anchor.
     *
     * @param timeSinceStart seconds since decoder power-on from the latest PASSING
     * @return {@code true} if a decoder restart was detected and the epoch was reset
     */
    public synchronized boolean detectsRestart(double timeSinceStart) {
        if (lastTimeSinceStart != null && timeSinceStart < lastTimeSinceStart - 10.0) {
            // Decoder restarted — reset epoch; next call will re-anchor
            decoderEpoch = null;
            // Note: lastTimeSinceStart is NOT reset here; the next conversion will update it
            return true;
        }
        return false;
    }
}
