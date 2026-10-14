package dev.monkeypatch.rctiming.decoderprotocol.timing;

/**
 * A {@link ParsedPassing} whose {@code timeSinceStart} has been converted to an absolute
 * epoch-anchored UTC timestamp by {@link EpochAnchor}.
 *
 * <p>This is the value the app publishes as a LapPassingEvent.
 */
public record EpochCorrectedPassing(
    String transponderNumber,
    long   rtcTimeMicros,
    int    seqNum,
    int    decoderId,
    int    signalStrength,
    int    hitCount
) {}
