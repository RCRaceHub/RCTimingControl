package dev.monkeypatch.rctiming.timing.split.dto;

import java.util.List;

/**
 * One car's sector times on a split-loop track (#676), in milliseconds, one entry per sector in track order.
 * {@code lastMs} is the car's latest complete lap; {@code bestMs} is its fastest time in each sector so far.
 * {@code lapNumber} is the lap {@code lastMs} came from.
 */
public record SectorRowDto(long entryId, int lapNumber, List<Long> lastMs, List<Long> bestMs) {
}
