-- Shikabane eliminations: a car that flips, breaks down or stalls is "dead" and its elimination order sets its
-- heat points. Race control records each death by hand, since a decoder cannot tell a dead car from a slow one.
--
-- eliminated_at: when race control marked the car dead (UTC microseconds); the order of these is the elimination order.
-- revived_at: set when a zombie (a dead car flipped back onto its wheels by another car) goes back into the race.
--   A revived row no longer counts as an elimination; it stays as the record of the revival, which is worth a
--   bonus point. If the car dies again, a new row is added.
CREATE TABLE race_eliminations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id BIGINT NOT NULL,
    entry_id BIGINT NOT NULL,
    eliminated_at BIGINT NOT NULL,
    eliminated_by BIGINT NOT NULL,
    revived_at BIGINT,
    revived_by BIGINT,
    CONSTRAINT race_eliminations_race_id_fkey FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE,
    CONSTRAINT race_eliminations_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES entries(id)
);
CREATE INDEX idx_race_eliminations_race_id ON race_eliminations (race_id);
