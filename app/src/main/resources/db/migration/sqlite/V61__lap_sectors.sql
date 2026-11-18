-- The sector times of every lap of a finished race, for clubs with split decoders (#676). Written once, when the race
-- finishes, from the sector times the timing thread worked out as the laps were driven; a club with one decoder never
-- gets a row. sector_number is 1 for the first sector (start/finish loop to the first split), up to the last sector
-- (the last split to the finish loop). duration_ms is the sector time in milliseconds.
CREATE TABLE lap_sectors (
    race_id       BIGINT  NOT NULL,
    entry_id      BIGINT  NOT NULL,
    lap_number    INTEGER NOT NULL,
    sector_number INTEGER NOT NULL,
    duration_ms   BIGINT  NOT NULL,
    PRIMARY KEY (race_id, entry_id, lap_number, sector_number),
    CHECK (sector_number BETWEEN 1 AND 10),
    CHECK (duration_ms > 0),
    FOREIGN KEY (race_id) REFERENCES races (id) ON DELETE CASCADE
);
