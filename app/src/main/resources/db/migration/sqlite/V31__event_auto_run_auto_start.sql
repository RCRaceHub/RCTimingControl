-- Auto run starts itself (#338).
--
-- events.auto_run_auto_start: whether auto run switches itself on when a race of the event is started by hand,
--   so the races carry on without anyone pressing Go. On unless race control stops auto run.
ALTER TABLE events ADD COLUMN auto_run_auto_start BOOLEAN NOT NULL DEFAULT 1 CHECK (auto_run_auto_start IN (0, 1));
