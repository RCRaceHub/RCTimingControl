-- An official confirmed that a car in a finished qualifying heat did race, even though the decoder never saw it cross
-- the line, so it is not marked did not start automatically. 0 for every existing row.
ALTER TABLE race_entries ADD COLUMN confirmed_started BOOLEAN NOT NULL DEFAULT 0 CHECK (confirmed_started IN (0, 1));
