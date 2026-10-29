package dev.monkeypatch.rctiming.query.audit;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * One audit row as the admin API returns it. {@code before} and {@code after} are the stored JSON, or null.
 *
 * @param at          when the change happened
 * @param actorUserId the official's user id, or null for a system job or the command line
 * @param actor       the stored actor label; it may include the official's email, unlike
 *                    {@link AuditActors#readable}
 * @param source      where the change came from, as recorded
 * @param entityType  the kind of thing changed, such as {@code entry}
 * @param entityId    the changed thing's id as text
 * @param eventId     the meeting it relates to, or null
 * @param raceId      the race it relates to, or null
 */
public record AuditEntryDto(
        Long id,
        Instant at,
        Long actorUserId,
        String actor,
        String source,
        String action,
        String entityType,
        String entityId,
        Long eventId,
        Long raceId,
        String summary,
        JsonNode before,
        JsonNode after) {
}
