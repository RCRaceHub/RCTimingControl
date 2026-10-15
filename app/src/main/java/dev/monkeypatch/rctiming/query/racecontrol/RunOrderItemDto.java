package dev.monkeypatch.rctiming.query.racecontrol;

import java.time.Instant;

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
