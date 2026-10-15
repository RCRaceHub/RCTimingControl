-- The RaceHub address results are sent to, set from the admin panel. NULL means the club has not set one, and
-- rctiming.racehub.results-url in application.properties is used instead.
ALTER TABLE club_profiles ADD COLUMN racehub_results_url TEXT;
