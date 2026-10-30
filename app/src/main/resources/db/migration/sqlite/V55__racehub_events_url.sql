-- Where RCTC asks RaceHub which events are ready to be pulled for timing (the "Find RaceHub events" picker).
-- NULL means the club has not set one. The club's key for RaceHub is the same one used for results, from the
-- settings file (rctiming.racehub.token), so it is not stored here.
ALTER TABLE club_profiles ADD COLUMN racehub_events_url TEXT;
