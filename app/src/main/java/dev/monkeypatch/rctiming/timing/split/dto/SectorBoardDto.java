package dev.monkeypatch.rctiming.timing.split.dto;

import java.util.List;

/**
 * A race's sector times (#676), broadcast on {@code /topic/race/{raceId}/sectors} and served by the boards endpoint.
 * {@code sectorCount} is how many sectors a lap has: one more than the enabled split decoders, and 0 when there are
 * none, so a client with a single decoder shows no sector columns at all. {@code overallBestMs} is the fastest time
 * anyone has set in each sector so far, an empty list or null entries before anyone has one.
 */
public record SectorBoardDto(int sectorCount, List<Long> overallBestMs, List<SectorRowDto> rows) {

    /** A race with no split decoders, or none seen yet. */
    public static SectorBoardDto none() {
        return new SectorBoardDto(0, List.of(), List.of());
    }
}
