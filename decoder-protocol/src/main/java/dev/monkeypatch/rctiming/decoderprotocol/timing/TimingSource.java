package dev.monkeypatch.rctiming.decoderprotocol.timing;

/**
 * Timing data source interface (TIMING-05).
 *
 * <p>Implementations connect to an AMB decoder (or simulator) and push parsed passings
 * to the registered callback. {@link AmbRc4TimingSource} is the only one so far; a P3
 * binary source would be another, with no change to race control or timing logic.
 *
 * <p>Lifecycle: call {@link #start()} once; call {@link #stop()} to release resources.
 * Both methods are idempotent.
 */
public interface TimingSource {
    /** Start consuming passings; non-blocking. */
    void start();

    /** Stop and release all resources. Idempotent. */
    void stop();
}
