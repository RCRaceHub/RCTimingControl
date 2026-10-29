-- Placeholder headshots, car photos and car colours for the demo club (#684), so the overlay's headshot and colour
-- options and the referee screen's swatch show something out of the box. The pictures are generated SVG avatars and
-- cars shipped with the app under /demo (frontend/public/demo), not real people. All eight V2000 drivers have a headshot.
-- Dave Quick, Sam Speed, Jo Turner and Pat Drift also have a car photo and colours, Kim Apex, Lee Grid and Max Lap
-- colours only (so the swatch shows), and Nina Pole nothing more, so her cars show the class colour. A separate
-- migration rather than an edit to V2000, so a demo database made before it gets them too. The inserts ignore a row
-- an official already added by hand (each table is unique per competitor or entry), and the class colours are set
-- only where none is chosen, so this never fails to start a kept demo database or overwrites a choice.

UPDATE racing_classes SET colour = '#2563eb' WHERE name = '13.5 Touring' AND colour IS NULL;
UPDATE racing_classes SET colour = '#16a34a' WHERE name = 'Stock Buggy' AND colour IS NULL;
UPDATE racing_classes SET colour = '#f59e0b' WHERE name = 'F1 Open' AND colour IS NULL;

INSERT OR IGNORE INTO competitor_media (competitor_id, headshot_url, sponsors)
SELECT c.id, m.url, ''
FROM (
    SELECT 'Dave Quick' AS name, '/demo/headshots/dave-quick.svg' AS url
    UNION ALL SELECT 'Sam Speed' AS name, '/demo/headshots/sam-speed.svg' AS url
    UNION ALL SELECT 'Jo Turner' AS name, '/demo/headshots/jo-turner.svg' AS url
    UNION ALL SELECT 'Pat Drift' AS name, '/demo/headshots/pat-drift.svg' AS url
    UNION ALL SELECT 'Kim Apex' AS name, '/demo/headshots/kim-apex.svg' AS url
    UNION ALL SELECT 'Lee Grid' AS name, '/demo/headshots/lee-grid.svg' AS url
    UNION ALL SELECT 'Max Lap' AS name, '/demo/headshots/max-lap.svg' AS url
    UNION ALL SELECT 'Nina Pole' AS name, '/demo/headshots/nina-pole.svg' AS url
) m
JOIN competitors c ON c.display_name = m.name;

INSERT OR IGNORE INTO entry_car_colours (entry_id, colours)
SELECT e.id, m.colours
FROM (
    SELECT 'Dave Quick' AS name, '#d62828,#f4d03f,#ffffff' AS colours
    UNION ALL SELECT 'Sam Speed' AS name, '#1d4ed8,#ffffff' AS colours
    UNION ALL SELECT 'Jo Turner' AS name, '#16a34a,#111111' AS colours
    UNION ALL SELECT 'Pat Drift' AS name, '#f97316' AS colours
    UNION ALL SELECT 'Kim Apex' AS name, '#7c3aed,#facc15' AS colours
    UNION ALL SELECT 'Lee Grid' AS name, '#0ea5e9' AS colours
    UNION ALL SELECT 'Max Lap' AS name, '#e11d48,#ffffff,#111111' AS colours
) m
JOIN competitors c ON c.display_name = m.name
JOIN entries e ON e.competitor_id = c.id;

INSERT OR IGNORE INTO entry_photos (entry_id, car_photo_url)
SELECT e.id, m.url
FROM (
    SELECT 'Dave Quick' AS name, '/demo/cars/dave-quick.svg' AS url
    UNION ALL SELECT 'Sam Speed' AS name, '/demo/cars/sam-speed.svg' AS url
    UNION ALL SELECT 'Jo Turner' AS name, '/demo/cars/jo-turner.svg' AS url
    UNION ALL SELECT 'Pat Drift' AS name, '/demo/cars/pat-drift.svg' AS url
) m
JOIN competitors c ON c.display_name = m.name
JOIN entries e ON e.competitor_id = c.id;
