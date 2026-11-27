package dev.monkeypatch.rctiming.decoderprotocol.timing;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class Rc4TextParserTest {

    private final Rc4TextParser parser = new Rc4TextParser();

    // Sample from docs/AMB_DECODER_PROTOCOL.md (SOH stripped by inbound handler)
    private static final String PASSING_LINE = "@\t20\t2\t8533156\t596.126\t400\t163\t2\tx14F5";

    @Test
    void parsesPassingRecordWithNineFields() {
        Optional<ParsedPassing> result = parser.parse(PASSING_LINE);
        assertThat(result).isPresent();
        ParsedPassing pp = result.get();
        assertThat(pp.decoderId()).isEqualTo(20);
        assertThat(pp.seqNum()).isEqualTo(2);
        assertThat(pp.transponderNumber()).isEqualTo("8533156");
    }

    @Test
    void parsesStatusRecordAsEmpty() {
        Optional<ParsedPassing> result = parser.parse("#\t20\t0\t72\t0\tx6B89");
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForMalformedLine() {
        assertThat(parser.parse("garbage")).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("@\t20\ttwo_fields_only")).isEmpty();
    }

    @Test
    void parsesTransponderNumberAsString() {
        Optional<ParsedPassing> result = parser.parse("@\t20\t1\t12345\t12.345\t300\t130\t2\txDEAD");
        assertThat(result).isPresent();
        assertThat(result.get().transponderNumber()).isEqualTo("12345");
    }

    @Test
    void parsesTimeSinceStartAsDouble() {
        Optional<ParsedPassing> result = parser.parse("@\t20\t1\t12345\t12.345\t300\t130\t2\txDEAD");
        assertThat(result).isPresent();
        assertThat(result.get().timeSinceStartSeconds()).isEqualTo(12.345d);
    }

    @Test
    void extractsSeqNumFromPassingRecord() {
        assertThat(parser.extractSeqNum(PASSING_LINE)).isEqualTo(OptionalInt.of(2));
    }

    @Test
    void extractsSeqNumFromStatusRecord() {
        assertThat(parser.extractSeqNum("#\t20\t0\t72\t0\tx6B89")).isEqualTo(OptionalInt.of(0));
    }

    @Test
    void extractSeqNumReturnsEmptyForMalformedLine() {
        assertThat(parser.extractSeqNum("garbage")).isEmpty();
        assertThat(parser.extractSeqNum("")).isEmpty();
        assertThat(parser.extractSeqNum(null)).isEmpty();
        assertThat(parser.extractSeqNum("@\t20")).isEmpty();
        assertThat(parser.extractSeqNum("@\t20\tnot_a_number")).isEmpty();
    }

    @Test
    void readsTimeSinceStartExactlyInMicroseconds() {
        // 596.126 and 1175.823 are not exact as doubles; the digits must come through unchanged
        assertThat(parser.parse("@\t20\t1\t12345\t596.126\t300\t130\t2\txDEAD").get().timeSinceStartMicros())
                .isEqualTo(596_126_000L);
        assertThat(parser.parse("@\t20\t1\t12345\t1175.823\t300\t130\t2\txDEAD").get().timeSinceStartMicros())
                .isEqualTo(1_175_823_000L);
    }
}
