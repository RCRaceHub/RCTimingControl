package dev.monkeypatch.rctiming.decoderprotocol.timing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock monitor: steady, jittery, drifting and restarted decoders, judged from passings alone. */
class ClockMonitorTest {

    private static final long SERVER_OFFSET = 1_700_000_000_000_000L;

    /** Feeds a passing every {@code stepSeconds} for {@code count} passings; {@code delay} gives each one's delay (µs) and {@code rate} scales the decoder clock. */
    private static ClockHealth feed(ClockMonitor monitor, int count, double stepSeconds, double rate,
                                    java.util.function.IntToLongFunction delay) {
        ClockHealth last = monitor.health();
        for (int i = 0; i < count; i++) {
            long realMicros = Math.round(i * stepSeconds * 1_000_000L);
            long decoderMicros = Math.round(realMicros * rate);
            last = monitor.observe(decoderMicros, SERVER_OFFSET + realMicros + delay.applyAsLong(i));
        }
        return last;
    }

    @Test
    void learnsBeforeItJudges() {
        ClockMonitor monitor = new ClockMonitor();

        ClockHealth health = feed(monitor, 5, 10, 1.0, i -> 2_000);

        assertThat(health.status()).isEqualTo(ClockHealth.Status.LEARNING);
    }

    @Test
    void aSteadyDecoderWithAFewMillisecondsOfDelayIsOk() {
        ClockMonitor monitor = new ClockMonitor();
        Random random = new Random(1);

        ClockHealth health = feed(monitor, 60, 10, 1.0, i -> 2_000 + random.nextInt(8_000));

        assertThat(health.status()).isEqualTo(ClockHealth.Status.OK);
        assertThat(health.jitterMs()).isLessThan(10);
        assertThat(health.driftMsPerMinute()).isBetween(-1.0, 1.0);
    }

    @Test
    void widelyVaryingDelayIsJittery() {
        ClockMonitor monitor = new ClockMonitor();

        ClockHealth health = feed(monitor, 40, 10, 1.0, i -> i % 2 == 0 ? 1_000 : 400_000);

        assertThat(health.status()).isEqualTo(ClockHealth.Status.JITTERY);
        assertThat(health.jitterMs()).isGreaterThan(300);
    }

    @Test
    void aDecoderClockRunningSlowIsDriftingAndReportsTheRate() {
        ClockMonitor monitor = new ClockMonitor();

        // 30 minutes of a passing every 10 s; the decoder loses 0.1 % (60 ms a minute)
        ClockHealth health = feed(monitor, 180, 10, 0.999, i -> 3_000);

        assertThat(health.status()).isEqualTo(ClockHealth.Status.DRIFTING);
        assertThat(health.driftMsPerMinute()).isBetween(55.0, 65.0);
    }

    @Test
    void aFastDecoderClockReadsAsNegativeDrift() {
        ClockMonitor monitor = new ClockMonitor();

        ClockHealth health = feed(monitor, 180, 10, 1.001, i -> 3_000);

        assertThat(health.status()).isEqualTo(ClockHealth.Status.DRIFTING);
        assertThat(health.driftMsPerMinute()).isNegative();
    }

    @Test
    void aRestartIsReportedThenForgottenAfterAFewPassings() {
        ClockMonitor monitor = new ClockMonitor();
        feed(monitor, 30, 10, 1.0, i -> 2_000);

        monitor.restarted();

        assertThat(monitor.health().status()).isEqualTo(ClockHealth.Status.RESTARTED);
        assertThat(monitor.health().restarts()).isEqualTo(1);
        ClockHealth later = feed(monitor, 40, 10, 1.0, i -> 2_000);
        assertThat(later.status()).isEqualTo(ClockHealth.Status.OK);
        assertThat(later.restarts()).isEqualTo(1);
    }

    @Test
    void theListenerHearsOnlyStatusChanges() {
        ClockMonitor monitor = new ClockMonitor();
        List<ClockHealth.Status> heard = new ArrayList<>();
        monitor.setListener(h -> heard.add(h.status()));

        feed(monitor, 30, 10, 1.0, i -> 2_000);
        monitor.restarted();

        assertThat(heard).containsExactly(ClockHealth.Status.OK, ClockHealth.Status.RESTARTED);
    }

    @Test
    void aStandingWarningIsToldAgainSoItsFiguresStayCurrent() {
        ClockMonitor monitor = new ClockMonitor();
        List<ClockHealth> heard = new ArrayList<>();
        monitor.setListener(heard::add);

        feed(monitor, 60, 10, 1.0, i -> i % 2 == 0 ? 1_000 : 400_000);

        assertThat(heard).hasSizeGreaterThan(2);
        assertThat(heard).allMatch(h -> h.status() == ClockHealth.Status.JITTERY || h.status() == ClockHealth.Status.OK);
    }

    @Test
    void aNoisyLinkEarlyOnIsNotMistakenForDrift() {
        ClockMonitor monitor = new ClockMonitor();
        Random random = new Random(7);

        // A passing every 6 s for four minutes with 0-90 ms of random delay on a healthy decoder
        ClockHealth health = feed(monitor, 40, 6, 1.0, i -> random.nextInt(90_000));

        assertThat(health.status()).isNotEqualTo(ClockHealth.Status.DRIFTING);
    }

    @Test
    void resettingOnReconnectStartsLearningAgainWithoutCountingARestart() {
        ClockMonitor monitor = new ClockMonitor();
        feed(monitor, 30, 10, 1.0, i -> 2_000);

        monitor.reset();

        assertThat(monitor.health().status()).isEqualTo(ClockHealth.Status.LEARNING);
        assertThat(monitor.health().restarts()).isZero();
    }

    @Test
    void aSecondRestartWhileStillRestartedDoesNotTellTheListenerAgain() {
        ClockMonitor monitor = new ClockMonitor();
        List<ClockHealth.Status> heard = new ArrayList<>();
        monitor.setListener(h -> heard.add(h.status()));

        monitor.restarted();
        monitor.restarted();

        assertThat(heard).containsExactly(ClockHealth.Status.RESTARTED);
        assertThat(monitor.health().restarts()).isEqualTo(2);
    }
}
