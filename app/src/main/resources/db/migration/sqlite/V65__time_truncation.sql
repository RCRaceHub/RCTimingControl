-- Whether the club cuts timing to hundredths of a second (truncated, never rounded) as some rule books ask, instead of
-- the decoder's milliseconds. 0 keeps milliseconds. One setting for the club, held with the live timing settings.
ALTER TABLE club_profiles ADD COLUMN truncate_to_hundredths BOOLEAN NOT NULL DEFAULT 0 CHECK (truncate_to_hundredths IN (0, 1));
