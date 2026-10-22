-- Selectable scoring systems (#436, tracker #435).
--
-- scoring_systems: a named set of scoring rules an official can pick, in two kinds. RACE systems say how heats and
--   finals are scored (rounds counted, tie handling, A final legs, cut-off window); CHAMPIONSHIP systems say how a
--   championship turns places into points (points scale, best X from Y, bonuses, overall table).
--   config is JSON with a "type" discriminator (RACE: CLASSIC, BRCA; CHAMPIONSHIP: PLACE_POINTS).
--   built_in = 1 rows are written here and are locked: the app refuses to change or delete them, and an official
--   clones one to change it. Nothing reads these rows yet; later changes let a race format and a championship pick one.
CREATE TABLE scoring_systems (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('RACE', 'CHAMPIONSHIP')),
    name VARCHAR(255) NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT 0 CHECK (built_in IN (0, 1)),
    config TEXT NOT NULL CHECK (json_valid(config)),
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT scoring_systems_kind_name_key UNIQUE (kind, name)
);

INSERT INTO scoring_systems (kind, name, built_in, config) VALUES
('RACE', 'Club default', 1,
 '{"type":"CLASSIC","bestHeatsCount":0,"splitTiedPlaces":false,"maxRaceWindowSeconds":0,"finalLegs":1,"legsCounted":0,"carsPerFinal":0,"fillTopFirst":false}'),
('RACE', 'BRCA 1/10 off-road', 1,
 '{"type":"BRCA","bestHeatsCount":2,"splitTiedPlaces":true,"maxRaceWindowSeconds":390,"finalLegs":3,"legsCounted":2,"carsPerFinal":10,"fillTopFirst":true}'),
('CHAMPIONSHIP', 'Club default', 1,
 '{"type":"PLACE_POINTS","bestX":null,"bestY":null,"tqBonusPoints":0,"aFinalWinnerBonusPoints":0,"showOverallTable":false,"pointsScale":[]}'),
('CHAMPIONSHIP', 'BRCA 1/10 off-road', 1,
 '{"type":"PLACE_POINTS","bestX":null,"bestY":null,"tqBonusPoints":0,"aFinalWinnerBonusPoints":0,"showOverallTable":false,"pointsScale":[{"position":1,"points":100},{"position":2,"points":95},{"position":3,"points":91},{"position":4,"points":88},{"position":5,"points":85},{"position":6,"points":83},{"position":7,"points":81},{"position":8,"points":79},{"position":9,"points":77},{"position":10,"points":75}]}');
