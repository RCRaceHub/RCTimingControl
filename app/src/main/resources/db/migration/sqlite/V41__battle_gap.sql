-- The largest gap between adjacent cars, in milliseconds, that makes them an on-track battle (#505). 0 switches
-- battles off. One value for the club, as RCTC runs for one club.
ALTER TABLE club_profiles ADD COLUMN battle_gap_ms INTEGER NOT NULL DEFAULT 2000;
