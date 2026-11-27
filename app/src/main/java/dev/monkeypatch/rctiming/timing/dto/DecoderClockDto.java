package dev.monkeypatch.rctiming.timing.dto;

import dev.monkeypatch.rctiming.decoderprotocol.timing.ClockHealth;

/**
 * How the decoder's clock looks against the server's, part of {@link DecoderStatusDto}. Detection only: it changes no
 * time. {@code status} is one of {@code LEARNING}, {@code OK}, {@code JITTERY}, {@code DRIFTING} or
 * {@code RESTARTED}; the figures are in milliseconds, see {@link ClockHealth}.
 */
public record DecoderClockDto(String status, int passings, double jitterMs, double latencyMs, double driftMsPerMinute,
                              int restarts) {

    /** The reading as it goes to browsers. */
    public static DecoderClockDto from(ClockHealth health) {
        return new DecoderClockDto(health.status().name(), health.passings(), health.jitterMs(), health.latencyMs(),
                health.driftMsPerMinute(), health.restarts());
    }
}
