-- Placeholder headshots, car photos and car colours for the demo club (#684), so the overlay's headshot and colour
-- options and the referee screen's swatch show something out of the box. The pictures are generated SVG avatars and
-- cars shipped with the app under /demo (frontend/public/demo), not real people. Dave Quick to Jo Turner have a car photo
-- and colours, Pat Drift to Max Lap colours only (so the swatch shows), and Nina Pole nothing, so her cars show the
-- class colour. A separate migration rather than an edit to V2000, so a demo database made before it gets them too.

UPDATE racing_classes SET colour = '#2563eb' WHERE name = '13.5 Touring';
UPDATE racing_classes SET colour = '#16a34a' WHERE name = 'Stock Buggy';
UPDATE racing_classes SET colour = '#f59e0b' WHERE name = 'F1 Open';

INSERT INTO competitor_media (competitor_id, headshot_url, sponsors)
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

INSERT INTO entry_car_colours (entry_id, colours)
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

INSERT INTO entry_photos (entry_id, car_photo_url)
SELECT e.id, m.url
FROM (
    SELECT 'Dave Quick' AS name, '/demo/cars/dave-quick.svg' AS url
    UNION ALL SELECT 'Sam Speed' AS name, '/demo/cars/sam-speed.svg' AS url
    UNION ALL SELECT 'Jo Turner' AS name, '/demo/cars/jo-turner.svg' AS url
    UNION ALL SELECT 'Pat Drift' AS name, '/demo/cars/pat-drift.svg' AS url
) m
JOIN competitors c ON c.display_name = m.name
JOIN entries e ON e.competitor_id = c.id;
