package dev.monkeypatch.rctiming.persistence;

import java.time.Instant;

/**
 * An entity whose row records when it last changed. {@link JooqRepository#save} sets the time on every save, to the
 * microsecond, so a caller never sets it.
 */
public interface UpdatedAt {

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);
}
