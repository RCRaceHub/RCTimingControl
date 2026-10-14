-- The club's own privacy notice (#267). NULL means the club has not written one, and the default text, filled in
-- from the club's name and contact details, is shown instead.
ALTER TABLE club_profiles ADD COLUMN privacy_notice TEXT;
