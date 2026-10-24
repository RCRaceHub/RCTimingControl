package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;

/**
 * A saved transponder swap, the answer of
 * {@code POST /race-control/events/{eventId}/entries/{entryId}/transponder-swap}.
 * {@code newTransponderNumber} is null when a secondary, third or fourth was removed.
 */
public record TransponderSwapResponse(
        long entryId,
        TransponderSlot slot,
        String oldTransponderNumber,
        String newTransponderNumber
) {}
