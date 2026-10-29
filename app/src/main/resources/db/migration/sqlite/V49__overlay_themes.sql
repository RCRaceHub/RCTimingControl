-- Saved overlay themes: a named look for the streaming overlay (Overlay Designer). design is a JSON object holding the
-- designer's choices (parts shown, layout, light or dark, ticker items, separator image, colour key, sponsor bar and so
-- on) under the designer's own field names; the front end owns its shape. The overlay opens a theme by its id
-- (/boards/overlay?look=ID). Two starter themes are seeded, BRCA and Club.
CREATE TABLE overlay_themes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    design TEXT NOT NULL CHECK (json_valid(design)),
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT overlay_themes_name_key UNIQUE (name)
);

INSERT INTO overlay_themes (name, design) VALUES
('BRCA',
 '{"theme":"dark","layout":"tower","top":10,"showClass":true,"showChart":true,"showSponsors":true,"showQualifying":true,"gap":"auto","gapEverySeconds":6,"showPredicted":true,"showBars":true,"showArrows":true,"showBattles":true,"showLegend":false,"separatorImage":null,"tickerItems":["next","drivers","marshals","tq","fastest","gain","loss","pace"]}'),
('Club',
 '{"theme":"light","layout":"tower","top":8,"showClass":true,"showChart":false,"showSponsors":true,"showQualifying":false,"gap":"int","gapEverySeconds":6,"showPredicted":true,"showBars":true,"showArrows":true,"showBattles":true,"showLegend":false,"separatorImage":null,"tickerItems":["next","drivers","marshals","fastest"]}');
