-- Shikabane false starts: a car that jumps the start is given penalty points in that heat, on top of its place.
-- points is the number applied when the penalty was given, so a later change to the format does not rewrite it.
-- applied_at: UTC microseconds.
CREATE TABLE race_point_penalties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    race_id BIGINT NOT NULL,
    entry_id BIGINT NOT NULL,
    points INTEGER NOT NULL,
    reason VARCHAR(200),
    applied_by BIGINT NOT NULL,
    applied_at BIGINT NOT NULL,
    CONSTRAINT race_point_penalties_race_id_fkey FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE,
    CONSTRAINT race_point_penalties_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES entries(id)
);
CREATE INDEX idx_race_point_penalties_race_id ON race_point_penalties (race_id);
