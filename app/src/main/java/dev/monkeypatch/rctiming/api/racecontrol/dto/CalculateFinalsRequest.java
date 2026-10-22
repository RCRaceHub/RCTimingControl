package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The optional body of {@code POST /race-control/event/{eventId}/qualifying/{eventClassId}/seed-finals}.
 *
 * @param carsPerFinal cars on each final's grid; null uses the class format's own grid size, and the grid grows
 *                     if the field would not fit
 */
public record CalculateFinalsRequest(@Min(1) @Max(100) Integer carsPerFinal) {
}
