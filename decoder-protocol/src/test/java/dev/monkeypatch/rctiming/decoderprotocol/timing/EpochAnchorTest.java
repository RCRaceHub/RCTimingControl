package dev.monkeypatch.rctiming.decoderprotocol.timing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EpochAnchorTest {

    @Test
    void firstPassingAnchorsEpochToInstantNow() {
        EpochAnchor anchor = new EpochAnchor();
        long beforeMs = System.currentTimeMillis();
        long micros = anchor.toRtcTimeMicros(10.0);
        long afterMs = System.currentTimeMillis();
        // First call: epoch anchored at now, result should be close to now in micros
        assertThat(micros).isGreaterThanOrEqualTo(beforeMs * 1000L);
        assertThat(micros).isLessThanOrEqualTo((afterMs + 1) * 1000L);
    }

    @Test
    void subsequentPassingsComputeRtcMicrosFromOffset() {
        EpochAnchor anchor = new EpochAnchor();
        long firstMicros = anchor.toRtcTimeMicros(10.0);
        long secondMicros = anchor.toRtcTimeMicros(20.0);
        // Delta should be 10 seconds = 10_000_000 micros
        long deltaMicros = secondMicros - firstMicros;
        assertThat(deltaMicros).isEqualTo(10_000_000L);
    }

    @Test
    void epochSurvivesReconnect() {
        EpochAnchor anchor = new EpochAnchor();
        long first = anchor.toRtcTimeMicros(10.0);
        long second = anchor.toRtcTimeMicros(12.0);
        // 2-second delta — epoch is preserved, not reset between calls
        assertThat(second - first).isEqualTo(2_000_000L);
    }

    @Test
    void epochResetsOnDecoderRestart() {
        EpochAnchor anchor = new EpochAnchor();
        anchor.toRtcTimeMicros(100.0); // anchor epoch with high timeSinceStart
        anchor.toRtcTimeMicros(110.0); // advance lastTimeSinceStart to 110.0
        // Simulate decoder restart: new timeSinceStart is < lastTimeSinceStart - 10
        boolean restarted = anchor.detectsRestart(5.0); // 5.0 < 110.0 - 10 = 100.0
        assertThat(restarted).isTrue();
        // After reset, next toRtcTimeMicros call should re-anchor
        long beforeMs = System.currentTimeMillis();
        long newMicros = anchor.toRtcTimeMicros(5.0);
        long afterMs = System.currentTimeMillis();
        assertThat(newMicros).isGreaterThanOrEqualTo(beforeMs * 1000L);
        assertThat(newMicros).isLessThanOrEqualTo((afterMs + 1) * 1000L);
    }

    @Test
    void lapTimesAreExactDecoderDifferencesWhatEverTheAnchor() {
        EpochAnchor anchor = new EpochAnchor();
        long first = anchor.toRtcTimeMicrosExact(596_126_000L);
        long second = anchor.toRtcTimeMicrosExact(608_471_000L);
        assertThat(second - first).isEqualTo(12_345_000L);
    }

    @Test
    void hundredthsTruncateTheDecoderTimeNotTheAnchoredClock() {
        EpochAnchor anchor = new EpochAnchor();
        anchor.setTruncationMicros(10_000L);
        long first = anchor.toRtcTimeMicrosExact(596_126_000L);
        long second = anchor.toRtcTimeMicrosExact(608_479_000L);
        // 596.126 cuts to 596.12 and 608.479 to 608.47, so the lap is 12.35 s whatever the anchor
        assertThat(second - first).isEqualTo(12_350_000L);
        assertThat(first % 10_000L).isZero();
        assertThat(second % 10_000L).isZero();
    }
}
