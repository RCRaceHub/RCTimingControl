package dev.monkeypatch.rctiming.query.championship;

/**
 * A driver's result in one round (one linked event) of a championship, as part of a {@link StandingsRowDto}.
 * A DNS round has position 0 and points 0 but still counts towards the Y of best-X-from-Y. Position and points are
 * both 0 for an excluded round.
 */
public record RoundResultDto(
        int roundNumber,
        Long eventId,
        String eventName,
        int position,        // final position in that event (0 when not raced / excluded)
        int points,          // points awarded (0 when excluded / DNS / DQ)
        boolean excluded,    // CHAMP-02 / CHAMP-09 — admin exclusion for this round
        boolean dropped      // CHAMP-01 — worst rounds dropped below best-X-from-Y cutoff
) {}
