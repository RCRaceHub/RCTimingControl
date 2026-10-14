-- How a competitor's name is shown on public screens (#265): SHOW the full name, INITIALS only, or HIDE it.
-- Set by an official, for example for a junior. It belongs to the competitor, so it carries over to later
-- meetings, and a RaceHub or CSV re-import never touches it. Officials' own screens keep the full name.
ALTER TABLE competitors ADD COLUMN name_visibility VARCHAR(10) NOT NULL DEFAULT 'SHOW'
    CONSTRAINT competitors_name_visibility_check CHECK (name_visibility IN ('SHOW', 'INITIALS', 'HIDE'));
