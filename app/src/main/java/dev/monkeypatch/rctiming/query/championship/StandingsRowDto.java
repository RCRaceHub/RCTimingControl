package dev.monkeypatch.rctiming.query.championship;

import java.util.List;

/**
 * One row of championship standings per driver-within-class.
 *
 * @param driverId    the competitor's id (L5, #13); exclusions use the same id
 * @param displayName the competitor's display name; empty if no grid in the championship named them
 * @param racingClassId the class the row is for; a driver who raced two classes has a row in each
 * @param totalPoints sum of best-X rounds plus any bonuses (CHAMP-01, CHAMP-07, CHAMP-08)
 * @param rounds      per-round detail — position, points, excluded (CHAMP-02/09), dropped (CHAMP-01 worst-round drop)
 */
public record StandingsRowDto(
        Long driverId,
        String displayName,
        Long racingClassId,
        int totalPoints,
        List<RoundResultDto> rounds
) {}
