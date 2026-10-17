package dev.monkeypatch.rctiming.query.competitor;

import java.time.Instant;

/**
 * One change to a competitor: when, who, and the spoken name before and after (null means none).
 *
 * @param by     the full name of the official who made the change
 * @param action what was done, as recorded in the competitor's audit log
 */
public record CompetitorChangeDto(
        Instant at,
        String by,
        String action,
        String before,
        String after) {
}
