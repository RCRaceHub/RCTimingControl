-- Saved board views (#481): a named screen an official builds from panels (live timing, live top table, next race,
-- marshals, last result), opened by its address /boards/view/{slug}, so each trackside monitor or the commentator can
-- have its own. panels is a JSON array of {"type", "top", "span"}; columns is how many columns the page lays them in.
CREATE TABLE board_views (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    columns INTEGER NOT NULL DEFAULT 2 CHECK (columns BETWEEN 1 AND 3),
    panels TEXT NOT NULL CHECK (json_valid(panels)),
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT board_views_slug_key UNIQUE (slug)
);

INSERT INTO board_views (slug, name, columns, panels) VALUES
('commentator', 'Commentator', 2,
 '[{"type":"LIVE_TIMING","top":null,"span":2},{"type":"LIVE_TOP","top":10,"span":1},{"type":"NEXT_RACE","top":null,"span":1}]'),
('monitor', 'Trackside monitor', 2,
 '[{"type":"LIVE_TIMING","top":null,"span":1},{"type":"LIVE_TOP","top":10,"span":1},{"type":"MARSHALS","top":null,"span":1},{"type":"NEXT_RACE","top":null,"span":1}]');
