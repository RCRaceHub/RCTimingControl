package dev.monkeypatch.rctiming.query.audit;

import java.time.Instant;

/**
 * What to look for in the audit log. Every field is optional; the ones given must all match.
 *
 * @param from earliest time to include (inclusive)
 * @param to   latest time to include (exclusive)
 */
public record AuditFilter(
        String entityType,
        String entityId,
        String action,
        Long actorUserId,
        Long eventId,
        Long raceId,
        Instant from,
        Instant to) {
}
