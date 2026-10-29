-- Sponsors owned by an overlay theme: a name and an optional logo each, in sort_order. When a theme has any, the
-- overlay opened with that theme (?look=ID) shows them in its sponsor bar instead of the event's sponsors
-- (event_sponsors); a theme with none falls back to the event's. logo_url is the address the logo is served from.
CREATE TABLE overlay_theme_sponsors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    theme_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT overlay_theme_sponsors_theme_id_fkey FOREIGN KEY (theme_id) REFERENCES overlay_themes(id) ON DELETE CASCADE
);
CREATE INDEX idx_overlay_theme_sponsors_theme_id ON overlay_theme_sponsors (theme_id);
