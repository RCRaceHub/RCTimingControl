-- RaceHub now identifies a class by its club-level id (club_class_id), stable across meetings, instead of the
-- per-meeting event_class_id. A per-meeting id says nothing about the club class, so none of what was keyed by it
-- can be carried over by id:
--   * entries.racehub_event_class_id becomes racehub_club_class_id, emptied. The next import of an entry fills it.
--   * Every class link is dropped: its key was a per-meeting id, and is now a club class id. The RaceHub classes
--     screen suggests the racing class for each club class by name, and an official confirms; imports stay held
--     until a class is linked.
--   * Per-meeting mappings are dropped, except the CSV import's (CSV:<class name>), which still need them.
ALTER TABLE entries RENAME COLUMN racehub_event_class_id TO racehub_club_class_id;
UPDATE entries SET racehub_club_class_id = NULL;
DELETE FROM racehub_class_links;
DELETE FROM racehub_class_mappings WHERE racehub_event_class_id NOT LIKE 'CSV:%';
