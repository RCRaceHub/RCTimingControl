-- Auto run no longer switches itself on by default.
--
-- events.auto_run_auto_start: a race started by hand switched auto run on for every event (default 1), which
--   surprised race directors who never pressed Go. It is now an opt-in; this turns it off for existing events.
--   New events get 0 from the application (Event.autoRunAutoStart), which writes the column on insert.
UPDATE events SET auto_run_auto_start = 0;
