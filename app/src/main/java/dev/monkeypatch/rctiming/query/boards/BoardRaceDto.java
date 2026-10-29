package dev.monkeypatch.rctiming.query.boards;

/**
 * A race as the spectator boards show it (L12).
 *
 * @param label       e.g. "Qualifying 2 — Stock Buggy — Heat 1" or "A Final — Stock Buggy"
 * @param roundType   the round's type name
 * @param finalLetter the final's letter (such as A), or null for a race that is not a final
 * @param status      the race's lifecycle status name
 * @param classColour the colour the club picked for the class as {@code #rrggbb}, or null for none (#482)
 * @param bumpUpPlaces how many places at the front of this final move up into the final above it when it ends
 *                    (that final's bump slots), or 0 when none do (#530)
 */
public record BoardRaceDto(
        long raceId,
        String label,
        String roundType,
        int roundNumber,
        String className,
        int heatNumber,
        String finalLetter,
        String status,
        String classColour,
        int bumpUpPlaces
) {}
