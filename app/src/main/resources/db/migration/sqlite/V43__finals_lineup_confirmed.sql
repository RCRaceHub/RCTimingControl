-- The finals line-up review screen: when race control confirmed the event's finals line-ups after qualifying.
-- events.finals_lineup_confirmed_at: UTC microseconds, null until confirmed. Calculating the finals again clears it,
--   so the review screen comes back for the new line-ups.
ALTER TABLE events ADD COLUMN finals_lineup_confirmed_at BIGINT;
