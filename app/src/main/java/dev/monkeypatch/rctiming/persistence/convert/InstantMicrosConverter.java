package dev.monkeypatch.rctiming.persistence.convert;

import org.jooq.impl.AbstractConverter;

import java.time.Instant;

/**
 * jOOQ side of {@link InstantMicros}: generated timestamp fields read and write {@link Instant}. The jOOQ code
 * generator applies it (a forced type in {@code app/build.gradle.kts}) to {@code BIGINT} columns named {@code *_AT}
 * and to the practice lap crossing time, so they surface as {@link Instant} in repositories and query classes.
 */
public class InstantMicrosConverter extends AbstractConverter<Long, Instant> {

    public InstantMicrosConverter() {
        super(Long.class, Instant.class);
    }

    @Override
    public Instant from(Long databaseObject) {
        return InstantMicros.fromMicros(databaseObject);
    }

    @Override
    public Long to(Instant userObject) {
        return InstantMicros.toMicros(userObject);
    }
}
