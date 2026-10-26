package dev.monkeypatch.rctiming.api.racecontrol.dto;

import java.util.Map;

/**
 * The optional body of {@code POST /race-control/event/{eventId}/seed-all-finals}.
 *
 * @param carsPerFinal cars on each final's grid by event class id, for the classes the official set a number for;
 *                     a class not listed uses its format's own grid size. Each value is 1 to 100.
 */
public record CalculateAllFinalsRequest(Map<Long, Integer> carsPerFinal) {
}
