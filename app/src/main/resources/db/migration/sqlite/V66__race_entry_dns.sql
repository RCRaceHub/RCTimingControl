-- A car an official marked "did not start" in a heat. The heat's result lists it last with no laps, flagged DNS, and
-- a low-points class scores it as a driver who did not run. 0 for every existing row.
ALTER TABLE race_entries ADD COLUMN dns BOOLEAN NOT NULL DEFAULT 0 CHECK (dns IN (0, 1));
