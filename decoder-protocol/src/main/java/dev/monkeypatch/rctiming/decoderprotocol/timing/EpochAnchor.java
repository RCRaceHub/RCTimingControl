package dev.monkeypatch.rctiming.decoderprotocol.timing;

import java.time.Instant;

/**
 * Converts RC-4 {@code timeSinceStart_s} (seconds since decoder power-on) to
 * absolute wall-clock UTC microseconds by anchoring to the server clock, since the protocol
 * carries no absolute timestamp.
 *
 * <h3>Formula</h3>
 * <pre>
 * rtcTimeMicros = (decoderEpoch.toEpochMilli() + (timeSinceStart_s - firstTimeSinceStart_s) * 1000) * 1000L
 * </pre>
 * where {@code decoderEpoch} = {@code Instant.now()} captured at the FIRST PASSING record.
 *
 * <h3>Decoder restart detection</h3>
 * If {@code timeSinceStart_s} regresses by more than 10 seconds from the last seen value,
 * the decoder is assumed to have restarted. The epoch is reset so the next call to
 * {@link #toRtcTimeMicros(double)} re-anchors.
 *
 * <p>All methods are synchronized for thread safety (called from a single Netty I/O thread
 * in normal operation, but made safe for testing).
 */
public class EpochAnchor {

    private Instant decoderEpoch;
    private Double  firstTimeSinceStart;
    private Double  lastTimeSinceStart;

    /**
     * Convert {@code timeSinceStart_s} to epoch-anchored UTC microseconds.
     * On the very first call the epoch is anchored to {@code Instant.now()}.
     *
     * @param timeSinceStart seconds since decoder power-on
     * @return UTC timestamp in microseconds
     */
    public synchronized long toRtcTimeMicros(double timeSinceStart) {
        if (decoderEpoch == null) {
            // First PASSING — anchor epoch now
            decoderEpoch         = Instant.now();
            firstTimeSinceStart  = timeSinceStart;
        }
        lastTimeSinceStart = timeSinceStart;
        double offsetSeconds = timeSinceStart - firstTimeSinceStart;
        return (decoderEpoch.toEpochMilli() + (long)(offsetSeconds * 1000)) * 1000L;
    }

    /**
     * Check whether {@code timeSinceStart} indicates a decoder restart
     * (regression of more than 10 s below the last observed value).
     *
     * <p>If a restart is detected the epoch is reset; the next call to
     * {@link #toRtcTimeMicros(double)} will re-anchor.
     *
     * @param timeSinceStart seconds since decoder power-on from the latest PASSING
     * @return {@code true} if a decoder restart was detected and the epoch was reset
     */
    public synchronized boolean detectsRestart(double timeSinceStart) {
        if (lastTimeSinceStart != null && timeSinceStart < lastTimeSinceStart - 10.0) {
            // Decoder restarted — reset epoch; next toRtcTimeMicros call will re-anchor
            decoderEpoch        = null;
            firstTimeSinceStart = null;
            // Note: lastTimeSinceStart is NOT reset here; toRtcTimeMicros will update it
            return true;
        }
        return false;
    }
}
