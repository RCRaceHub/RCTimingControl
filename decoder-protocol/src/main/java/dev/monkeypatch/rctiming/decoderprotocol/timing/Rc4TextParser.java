package dev.monkeypatch.rctiming.decoderprotocol.timing;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure parser for the AMB RC-4 text protocol.
 *
 * <p>This class has no static state and no Spring dependencies. It converts a single
 * RC-4 text line into a {@link ParsedPassing}. The SOH (0x01) prefix byte MUST be
 * stripped by the caller (Rc4InboundHandler) before invoking {@link #parse(String)}.
 *
 * <p>Handles two record types:
 * <ul>
 *   <li>{@code @} — PASSING record; returns a populated {@link ParsedPassing}</li>
 *   <li>{@code #} — STATUS/heartbeat; returns empty</li>
 *   <li>Any other input, including malformed lines, returns empty</li>
 * </ul>
 */
public class Rc4TextParser {

    /**
     * Parse a single RC-4 text line (SOH already stripped).
     *
     * @param line the tab-separated line (no leading SOH, no trailing CRLF)
     * @return populated {@link ParsedPassing} for PASSING records; empty for STATUS or malformed
     */
    public Optional<ParsedPassing> parse(String line) {
        if (line == null || line.isEmpty()) return Optional.empty();
        String[] f = line.split("\t");
        if (f.length < 1) return Optional.empty();
        // A STATUS record ("#") carries no passing, so it falls to empty with anything malformed
        return "@".equals(f[0]) ? parsePassing(f) : Optional.empty();
    }

    /**
     * Extract the {@code seq_num} field from any record type (PASSING or STATUS), SOH already
     * stripped. STATUS and PASSING records share a single per-decoder sequence counter
     * (docs/AMB_DECODER_PROTOCOL.md) — gap detection must observe both record types' seq_num,
     * not just PASSING's, or every STATUS heartbeat interleaved between two PASSING records
     * reads as a false missed-record gap.
     *
     * @param line the tab-separated line (no leading SOH, no trailing CRLF)
     * @return the seq_num field for a well-formed PASSING or STATUS record; empty otherwise
     */
    public OptionalInt extractSeqNum(String line) {
        if (line == null || line.isEmpty()) return OptionalInt.empty();
        String[] f = line.split("\t");
        if (f.length < 3 || !(f[0].equals("@") || f[0].equals("#"))) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(f[2]));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private Optional<ParsedPassing> parsePassing(String[] f) {
        // Fields: @, decoderId, seqNum, transponderId, timeSinceStart, hits, strength, status, crc
        if (f.length < 9) return Optional.empty();
        try {
            int decoderId   = Integer.parseInt(f[1]);
            int seqNum      = Integer.parseInt(f[2]);
            String transponder = f[3];           // kept as String per LapPassingEvent contract
            double timeSinceStart = Double.parseDouble(f[4]);
            int hitCount    = Integer.parseInt(f[5]);
            int strength    = Integer.parseInt(f[6]);
            // f[7] = passingStatus, f[8] = crc — not stored in ParsedPassing
            return Optional.of(new ParsedPassing(transponder, timeSinceStart, seqNum, decoderId, strength, hitCount));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
