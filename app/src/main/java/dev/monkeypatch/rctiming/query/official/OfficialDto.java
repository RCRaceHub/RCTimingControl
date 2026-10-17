package dev.monkeypatch.rctiming.query.official;

import java.time.Instant;
import java.util.List;

/**
 * One official as the Officials page lists them (#61).
 *
 * @param roles      the role names the official holds, sorted alphabetically; empty if none
 * @param enabled    false once the official has been disabled
 * @param disabledAt when they were disabled, or null while enabled
 */
public record OfficialDto(
        long id,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        boolean enabled,
        Instant disabledAt,
        Instant createdAt
) {}
