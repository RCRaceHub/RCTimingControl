package dev.monkeypatch.rctiming.api.boards.dto;

import java.util.List;

/**
 * What the streaming overlay's ticker scrolls besides the live stats, answer of {@code GET /api/v1/boards/ticker}.
 * Names are as the public may see them (#265). Any part may be empty: {@code nextRace} is null when nothing is
 * left to run, {@code nextDrivers} are in grid order, {@code marshals} are the drivers who marshal that race,
 * and {@code topQualifiers} has one entry for each class that has finished a qualifying heat.
 */
public record TickerDto(
        String nextRace,
        List<String> nextDrivers,
        List<String> marshals,
        List<TopQualifier> topQualifiers
) {
    /** The driver on top of a class's qualifying table; {@code bestLapMs} is null without a timed lap. */
    public record TopQualifier(String className, String driverName, Long bestLapMs) {}
}
