-- Practice runs and named drivers (#151).
--
-- practice_sessions.pause_seconds: a transponder quiet for longer than this starts a new run, and the pause is not
--   counted as a lap.
-- practice_laps.run_number: the run a lap belongs to, counted from 1 for each transponder in the session.
-- practice_drivers: who a transponder is in one session, kept so naming it lasts and works with no event. The
--   competitor is a full competitor, so a walk-in added here can be matched to an entry later.
ALTER TABLE practice_sessions ADD COLUMN pause_seconds INTEGER NOT NULL DEFAULT 180;

ALTER TABLE practice_laps ADD COLUMN run_number INTEGER NOT NULL DEFAULT 1;

CREATE TABLE practice_drivers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    practice_session_id BIGINT NOT NULL,
    transponder_number VARCHAR(50) NOT NULL,
    competitor_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT practice_drivers_session_fkey FOREIGN KEY (practice_session_id) REFERENCES practice_sessions(id) ON DELETE CASCADE,
    CONSTRAINT practice_drivers_competitor_fkey FOREIGN KEY (competitor_id) REFERENCES competitors(id),
    CONSTRAINT practice_drivers_transponder_key UNIQUE (practice_session_id, transponder_number)
);
CREATE INDEX idx_practice_drivers_competitor ON practice_drivers (competitor_id);
