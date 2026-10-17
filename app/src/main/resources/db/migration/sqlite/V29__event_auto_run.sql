-- Automatic race progression (#338).
--
-- events.auto_run_gap_seconds: how long after a race finishes the next one starts when race control has
--   switched auto run on. 3 minutes unless changed.
-- events.auto_run_hold_before_finals: whether auto run stops after the last qualifier, so the finals can be
--   checked and seeded again before anyone starts them.
ALTER TABLE events ADD COLUMN auto_run_gap_seconds INTEGER NOT NULL DEFAULT 180 CHECK (auto_run_gap_seconds BETWEEN 0 AND 3600);
ALTER TABLE events ADD COLUMN auto_run_hold_before_finals BOOLEAN NOT NULL DEFAULT 1 CHECK (auto_run_hold_before_finals IN (0, 1));
