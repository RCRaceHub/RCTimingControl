package dev.monkeypatch.rctiming.timing.dto;

import java.util.List;

/**
 * The sector times of a finished race (#676), read from {@code lap_sectors}. {@code sectorCount} is 0 when the race
 * has none, which is every race at a club with a single decoder, so a results page shows no sector columns. Times are
 * milliseconds, one entry per sector in track order. {@code overallBestMs} is the fastest anyone went in each sector;
 * each driver's {@code bestMs} is their own fastest.
 */
public record SectorResultsDto(int sectorCount, List<Long> overallBestMs, List<DriverSectors> drivers) {

    /** One car's sector times: its best in each sector and every lap that had all of them. */
    public record DriverSectors(long entryId, List<Long> bestMs, List<LapSectors> laps) {
    }

    /** The sector times of one lap. */
    public record LapSectors(int lapNumber, List<Long> sectorMs) {
    }

    /** A race with no sector times. */
    public static SectorResultsDto none() {
        return new SectorResultsDto(0, List.of(), List.of());
    }
}
