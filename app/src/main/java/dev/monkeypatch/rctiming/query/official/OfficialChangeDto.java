package dev.monkeypatch.rctiming.query.official;

import java.time.Instant;

/**
 * One change to an official, newest first on the Officials page (#61). {@code actorName} is null
 * when the change came from the laptop's command line.
 *
 * @param id           the log row's id; later changes have higher ids
 * @param officialId   the official the change was made to
 * @param officialName that official's full name
 * @param action       what was done, as recorded
 * @param detail       extra text recorded with the action, if any
 * @param actorId      the official who made the change, or null
 */
public record OfficialChangeDto(
        long id,
        Instant at,
        long officialId,
        String officialName,
        String action,
        String detail,
        Long actorId,
        String actorName
) {}
