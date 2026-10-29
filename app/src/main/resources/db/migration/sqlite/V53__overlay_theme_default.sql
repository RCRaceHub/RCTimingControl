-- The club's default overlay theme: the look the plain overlay address (/boards/overlay, no ?look=) opens. At most one
-- theme is the default (the partial unique index); a club has one database, so the default is club-wide, not per event.
ALTER TABLE overlay_themes ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT 0 CHECK (is_default IN (0, 1));
CREATE UNIQUE INDEX idx_overlay_themes_default ON overlay_themes (is_default) WHERE is_default = 1;
