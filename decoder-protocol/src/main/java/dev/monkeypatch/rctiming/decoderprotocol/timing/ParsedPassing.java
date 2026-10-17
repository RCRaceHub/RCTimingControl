package dev.monkeypatch.rctiming.decoderprotocol.timing;

/**
 * Immutable record of a single PASSING event parsed from the RC-4 text protocol.
 * The {@code timeSinceStartSeconds} is relative to decoder power-on — use {@link EpochAnchor}
 * to convert to wall-clock UTC microseconds.
 *
 * @param transponderNumber     the transponder id as the decoder sent it, kept as text
 * @param timeSinceStartSeconds seconds since decoder power-on, not a Unix timestamp
 * @param seqNum                the decoder's sequence number, shared with STATUS records
 * @param decoderId             the decoder's id
 * @param signalStrength        the strength field of the PASSING record
 * @param hitCount              the hits field of the PASSING record
 */
public record ParsedPassing(
    String transponderNumber,
    double timeSinceStartSeconds,
    int seqNum,
    int decoderId,
    int signalStrength,
    int hitCount
) {}
