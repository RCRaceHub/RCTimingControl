-- Where the club publishes results: NONE, RACEHUB, SITE or BOTH (#319). NULL means the club has not chosen:
-- RaceHub when a RaceHub address is set (as before this setting), otherwise nothing.
ALTER TABLE club_profiles ADD COLUMN results_publish_target TEXT
    CHECK (results_publish_target IS NULL OR results_publish_target IN ('NONE', 'RACEHUB', 'SITE', 'BOTH'));
