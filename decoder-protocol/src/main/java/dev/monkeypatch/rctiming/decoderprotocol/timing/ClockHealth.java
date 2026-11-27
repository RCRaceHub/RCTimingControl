package dev.monkeypatch.rctiming.decoderprotocol.timing;

/**
 * What {@link ClockMonitor} makes of the decoder's clock against the server's, at one moment. It changes no time and
 * no result: it only tells race control when the decoder's clock or the link to it looks wrong. Figures are in
 * milliseconds.
 *
 * @param status          where the decoder's clock stands, see {@link Status}
 * @param passings        passings seen since the monitor last started learning (the first one, or a restart)
 * @param jitterMs        how far apart the delay of the last twenty passings was, the widest minus the narrowest
 * @param latencyMs       how much later the typical recent passing arrived than the fastest recent one; the delay the
 *                        link adds, which is all that can be known (the true one-way delay is not visible)
 * @param driftMsPerMinute how fast the decoder's clock is gaining or losing on the server's since the start, negative
 *                        when the decoder runs fast; 0 until there is enough to measure
 * @param restarts        how many times the decoder's clock was seen to start again since the app began
 */
public record ClockHealth(Status status, int passings, double jitterMs, double latencyMs, double driftMsPerMinute,
                          int restarts) {

    /** Where the decoder's clock stands. */
    public enum Status {
        /** Too few passings to judge yet. */
        LEARNING,
        /** Steady against the server clock. */
        OK,
        /** The delay between the decoder stamping a passing and the server receiving it varies widely. */
        JITTERY,
        /** The decoder's clock is gaining or losing on the server's. */
        DRIFTING,
        /**
         * The decoder's clock started again (a power cycle), so times before and after cannot be compared. Shown until
         * ten passings from the new run have come in.
         */
        RESTARTED
    }

    /** The state before anything has been seen. */
    public static ClockHealth initial() {
        return new ClockHealth(Status.LEARNING, 0, 0, 0, 0, 0);
    }
}
