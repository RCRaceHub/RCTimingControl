package dev.monkeypatch.rctiming.query.split;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.timing.split.dto.SectorResultsDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static dev.monkeypatch.rctiming.jooq.generated.tables.LapSectors.LAP_SECTORS;

/**
 * Reads the sector times stored for a finished race (#676): each car's best in every sector and every lap's sectors,
 * and the fastest of anyone. Read pool, one snapshot.
 */
@Component
@ReadTransaction
public class LapSectorQuery {

    private final DSLContext dsl;

    public LapSectorQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** The race's stored sector times, or {@link SectorResultsDto#none()} when it has none. */
    public SectorResultsDto resultsOf(long raceId) {
        // entry -> lap -> sector (1-based) -> ms
        Map<Long, TreeMap<Integer, TreeMap<Integer, Long>>> byEntry = new LinkedHashMap<>();
        int sectorCount = 0;
        for (var r : dsl.selectFrom(LAP_SECTORS).where(LAP_SECTORS.RACE_ID.eq(raceId))
                .orderBy(LAP_SECTORS.ENTRY_ID, LAP_SECTORS.LAP_NUMBER, LAP_SECTORS.SECTOR_NUMBER).fetch()) {
            byEntry.computeIfAbsent(r.getEntryId(), k -> new TreeMap<>())
                    .computeIfAbsent(r.getLapNumber(), k -> new TreeMap<>())
                    .put(r.getSectorNumber(), r.getDurationMs());
            sectorCount = Math.max(sectorCount, r.getSectorNumber());
        }
        if (sectorCount == 0) {
            return SectorResultsDto.none();
        }
        Long[] overall = new Long[sectorCount];
        List<SectorResultsDto.DriverSectors> drivers = new ArrayList<>();
        for (var entry : byEntry.entrySet()) {
            Long[] best = new Long[sectorCount];
            List<SectorResultsDto.LapSectors> laps = new ArrayList<>();
            for (var lap : entry.getValue().entrySet()) {
                List<Long> times = new ArrayList<>(Collections.nCopies(sectorCount, (Long) null));
                for (var sector : lap.getValue().entrySet()) {
                    int i = sector.getKey() - 1;
                    long ms = sector.getValue();
                    times.set(i, ms);
                    if (best[i] == null || ms < best[i]) {
                        best[i] = ms;
                    }
                    if (overall[i] == null || ms < overall[i]) {
                        overall[i] = ms;
                    }
                }
                laps.add(new SectorResultsDto.LapSectors(lap.getKey(), times));
            }
            drivers.add(new SectorResultsDto.DriverSectors(entry.getKey(), Arrays.asList(best), laps));
        }
        return new SectorResultsDto(sectorCount, Arrays.asList(overall), drivers);
    }
}
