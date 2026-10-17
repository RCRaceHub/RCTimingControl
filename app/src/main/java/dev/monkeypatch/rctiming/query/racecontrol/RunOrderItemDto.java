package dev.monkeypatch.rctiming.query.racecontrol;

import java.time.Instant;

/**
 * One race in an event's run order, as the race control left panel lists it ({@link RunOrderQuery}).
 *
 * @param sequenceInEvent the round's place in the event's run order
 * @param sequenceInRound the race's place within its round, which orders the heats of all classes in the round
 * @param startedAt       when the race started, or null until it has
 */
public record RunOrderItemDto(
        long raceId,
        long eventClassId,
        int sequenceInEvent,
        int roundNumber,
        String roundType,       // "PRACTICE" | "QUALIFIER" | "FINAL"
        String className,
        int heatNumber,
        String finalLetter,     // null for practice/qualifier
        String status,
        int sequenceInRound,
        Instant startedAt  // null until race starts
) {
}
