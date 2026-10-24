-- A colour the club picks for each racing class, shown on line-ups and live timing (#482).
-- Stored as #rrggbb; null means no colour.
ALTER TABLE racing_classes ADD COLUMN colour TEXT
    CHECK (colour IS NULL OR colour GLOB '#[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]');
