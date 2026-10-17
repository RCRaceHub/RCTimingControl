package dev.monkeypatch.rctiming.persistence.convert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps are stored as UTC microseconds since the epoch (#26). Both directions pass null through, and a
 * nanosecond-precise {@link Instant} loses its sub-microsecond part on the way in.
 */
public final class InstantMicros {

    private InstantMicros() {
    }

    public static Long toMicros(Instant instant) {
        return instant == null ? null : ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    public static Instant fromMicros(Long micros) {
        return micros == null ? null : Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
    }
}
