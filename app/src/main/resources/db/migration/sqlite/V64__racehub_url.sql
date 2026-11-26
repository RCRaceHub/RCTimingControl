-- The one RaceHub address the club sets (the host, such as https://racehub.example). Results, the event picker, the
-- class and format lists and the entry feeds all find their paths from it. NULL means none is saved here, so the
-- settings file's rctiming.racehub.url applies; racehub_results_url and racehub_events_url stay as fallbacks for a
-- club that set those two separately before this existed.
ALTER TABLE club_profiles ADD COLUMN racehub_url TEXT;

-- A club that saved the events address gets its host as the one address; the path was always /api/rctc/v1/events.
UPDATE club_profiles
   SET racehub_url = substr(racehub_events_url, 1, instr(racehub_events_url, '/api/rctc/v1/events') - 1)
 WHERE racehub_events_url IS NOT NULL
   AND instr(racehub_events_url, '/api/rctc/v1/events') > 0;

-- One that saved only the results address in the current form gets its host in the same way.
UPDATE club_profiles
   SET racehub_url = substr(racehub_results_url, 1, instr(racehub_results_url, '/api/rctc/v1/results') - 1)
 WHERE racehub_url IS NULL
   AND racehub_results_url IS NOT NULL
   AND instr(racehub_results_url, '/api/rctc/v1/results') > 0;
