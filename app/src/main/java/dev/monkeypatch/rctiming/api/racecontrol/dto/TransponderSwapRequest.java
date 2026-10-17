package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /race-control/events/{eventId}/entries/{entryId}/transponder-swap}. Swap one of an
 * entry's transponders. A blank {@code newTransponderNumber} removes the secondary transponder; the primary can
 * only be replaced.
 */
public record TransponderSwapRequest(
        @NotNull TransponderSlot slot,
        @Size(max = 20) String newTransponderNumber
) {}
