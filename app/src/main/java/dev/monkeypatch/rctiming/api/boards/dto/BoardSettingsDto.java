package dev.monkeypatch.rctiming.api.boards.dto;

/**
 * The club's thresholds a board draws with, the answer of {@code GET /api/v1/boards/settings} (#505), in milliseconds.
 * {@code closeGapMs} is how near the car ahead is shown as close (0 switches the amber row off); {@code lateMarginMs}
 * is how far past its average lap a car is before its lap bar turns red.
 */
public record BoardSettingsDto(int closeGapMs, int lateMarginMs) {
}
