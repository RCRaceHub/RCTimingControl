package dev.monkeypatch.rctiming.decoderprotocol.timing;

/**
 * A {@link ParsedPassing} whose {@code timeSinceStart} has been converted to an absolute
 * epoch-anchored UTC timestamp by {@link EpochAnchor}.
 *
 * <p>This is the value the app publishes as a LapPassingEvent.
 *
 * @param transponderNumber the transponder id as the decoder sent it, kept as text
 * @param rtcTimeMicros     the passing time as UTC microseconds since the Unix epoch, anchored to the server clock
 *                          (millisecond resolution, since {@link EpochAnchor} works in whole milliseconds)
 * @param seqNum            the decoder's sequence number for the record
 * @param decoderId         the decoder's id
 * @param signalStrength    the strength field of the passing record
 * @param hitCount          the hits field of the passing record
 */
public record EpochCorrectedPassing(
    String transponderNumber,
    long   rtcTimeMicros,
    int    seqNum,
    int    decoderId,
    int    signalStrength,
    int    hitCount
) {}
