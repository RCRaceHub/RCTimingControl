package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.LapSectorsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.LapSectors.LAP_SECTORS;

/**
 * Writes the {@code lap_sectors} table (#676): the sector times of every lap of a finished race. A plain repository,
 * because a row has no entity behind it. Reads go through {@code LapSectorQuery}. Writes must run in the caller's write
 * transaction.
 */
@Repository
public class LapSectorRepository {

    /**
     * One sector of one lap of one car. {@code lapNumber} is the sector service's own count of finish crossings, which
     * can differ from the credited lap number after marshal adjustments, so do not join it to lap history.
     * {@code sectorNumber} counts from 1; {@code durationMs} is the sector time.
     */
    public record Row(long entryId, int lapNumber, int sectorNumber, long durationMs) {
    }

    private final DSLContext dsl;

    public LapSectorRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Replaces everything stored for the race with {@code rows}, so saving twice never doubles it. */
    public void replaceForRace(long raceId, List<Row> rows) {
        deleteForRace(raceId);
        List<LapSectorsRecord> records = rows.stream()
                .map(r -> new LapSectorsRecord(raceId, r.entryId(), r.lapNumber(), r.sectorNumber(), r.durationMs()))
                .toList();
        dsl.batchInsert(records).execute();
    }

    /** Removes everything stored for the race. */
    public void deleteForRace(long raceId) {
        dsl.deleteFrom(LAP_SECTORS).where(LAP_SECTORS.RACE_ID.eq(raceId)).execute();
    }
}
