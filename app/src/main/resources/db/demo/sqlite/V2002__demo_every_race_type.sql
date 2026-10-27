-- One demo event for each way a class can be raced, each with a field sized to show it (heat splits, finals splits,
-- bump-ups, legs, promotion). All are OPEN with no run order, so the tester generates the rounds themselves.
-- Transponders 101-116 are the ones the simulator plays by default (RcTimingApplication.simulatorArguments).
-- Open practice is not an event; start it from Race control > Practice.

-- 1. the sixteen drivers: the eight from V2000 plus eight more, transponder number is 100 plus the roster position
INSERT INTO competitors (display_name) VALUES
    ('Ruth Chassis'), ('Omar Pinion'), ('Tess Droop'), ('Ivan Camber'),
    ('Hana Toe'), ('Zed Caster'), ('Fay Spur'), ('Gus Wishbone');

-- 2. an extra class for the Shikabane (marshal-less) fun-day events
INSERT INTO racing_classes (name, description) VALUES ('Shikabane Open', 'Fun-day elimination racing, any 1/10 car');

-- 3. formats not covered by V2000
INSERT INTO race_format_templates (name, config) VALUES
    ('Timed 5-min Grid Start', '{"type":"TIMED","durationMinutes":5,"startType":"GRID","qualifyingType":"FASTEST_LAP","racePaddingMinutes":1,"staggerIntervalSeconds":5,"finalsStartType":"GRID"}'),
    ('Bump-up Finals, 6 per final', '{"type":"BUMP_UP","qualifyingHeats":3,"heatDurationMinutes":5,"bestHeatsCount":2,"gridSize":6,"bumpSpots":2,"qualifyingStartType":"GRID","finalsStartType":"GRID","qualifyingType":"FTQ","racePaddingMinutes":2,"staggerIntervalSeconds":5}'),
    ('Points Finals, 4 per final', '{"type":"POINTS_FINALS","qualifyingHeats":3,"finalsCount":3,"finalDurationMinutes":5,"heatDurationMinutes":5,"qualifyingStartType":"STAGGER","finalsStartType":"GRID","qualifyingType":"FTQ","racePaddingMinutes":2,"staggerIntervalSeconds":5,"carsPerFinal":4,"fillTopFirst":true}'),
    ('Points Finals, BRCA legs', '{"type":"POINTS_FINALS","qualifyingHeats":3,"finalsCount":2,"finalDurationMinutes":5,"heatDurationMinutes":5,"qualifyingStartType":"GRID","finalsStartType":"GRID","qualifyingType":"ROUND_BY_ROUND","racePaddingMinutes":2,"staggerIntervalSeconds":5,"bestHeatsCount":2,"splitTiedPlaces":true,"brcaQualifying":true,"maxRaceWindowSeconds":400,"carsPerFinal":5,"fillTopFirst":true,"finalLegs":3,"legsCounted":2}'),
    ('Shikabane, laps and mains', '{"type":"SHIKABANE","heatDurationMinutes":5,"startType":"GRID","staggerIntervalSeconds":3,"falseStartPenaltyPoints":3,"zombieBonusPoints":1,"heatLaps":5,"mains":3,"mainGridSize":6,"promotionSpots":2,"zombieRound":true,"finalsStartType":"GRID"}'),
    ('Shikabane, timed heats', '{"type":"SHIKABANE","heatDurationMinutes":3,"startType":"STAGGER","staggerIntervalSeconds":3,"falseStartPenaltyPoints":3,"zombieBonusPoints":1,"heatLaps":0,"mains":0,"mainGridSize":0,"promotionSpots":0,"zombieRound":false}');

-- 4. one event per race type
INSERT INTO events (name, event_date, status, track_id) VALUES
    ('Demo: Timed, rolling start', '2026-11-07', 'OPEN', (SELECT id FROM tracks WHERE name = 'Rivermead Circuit')),
    ('Demo: Timed, grid start with finals', '2026-11-08', 'OPEN', (SELECT id FROM tracks WHERE name = 'Rivermead Circuit')),
    ('Demo: Bump-up finals', '2026-11-14', 'OPEN', (SELECT id FROM tracks WHERE name = 'Rivermead Circuit')),
    ('Demo: Points finals', '2026-11-15', 'OPEN', (SELECT id FROM tracks WHERE name = 'Parklands Arena')),
    ('Demo: Points finals in legs', '2026-11-21', 'OPEN', (SELECT id FROM tracks WHERE name = 'Parklands Arena')),
    ('Demo: Shikabane heats and mains', '2026-11-22', 'OPEN', (SELECT id FROM tracks WHERE name = 'Rivermead Circuit')),
    ('Demo: Shikabane timed heats', '2026-11-28', 'OPEN', (SELECT id FROM tracks WHERE name = 'Parklands Arena'));

-- 5. their classes (driver counts are in step 6)
INSERT INTO event_classes (config_snapshot, template_id, event_id, racing_class_id, finals_count, cars_per_final, bump_count)
SELECT t.config, t.id, (SELECT id FROM events WHERE name = c.event_name), (SELECT id FROM racing_classes WHERE name = c.class_name),
       c.finals, c.cars, c.bump
FROM (
    SELECT 'Demo: Timed, rolling start' AS event_name, '13.5 Touring' AS class_name, 'Timed 5-min Rolling Start' AS template, 1 AS finals, 8 AS cars, 0 AS bump
    UNION ALL SELECT 'Demo: Timed, grid start with finals', 'Stock Buggy', 'Timed 5-min Grid Start', 2, 6, 0
    UNION ALL SELECT 'Demo: Bump-up finals', '13.5 Touring', 'Bump-up Finals, 6 per final', 3, 6, 2
    UNION ALL SELECT 'Demo: Points finals', 'F1 Open', 'Points Finals, 4 per final', 3, 4, 0
    UNION ALL SELECT 'Demo: Points finals in legs', 'Stock Buggy', 'Points Finals, BRCA legs', 2, 5, 0
    UNION ALL SELECT 'Demo: Shikabane heats and mains', 'Shikabane Open', 'Shikabane, laps and mains', 3, 6, 0
    UNION ALL SELECT 'Demo: Shikabane timed heats', 'Shikabane Open', 'Shikabane, timed heats', 0, 0, 0
) c
JOIN race_format_templates t ON t.name = c.template;

-- 6. entries: the first N of the sixteen drivers in each event (N is the field size). Some drivers carry a
-- secondary transponder to try the primary-or-secondary match; they are only played if added to the simulator.
INSERT INTO entries (competitor_id, event_id, event_class_id, transponder_number, secondary_transponder_number, status)
SELECT comp.id, ec.event_id, ec.id, r.transponder, r.secondary, 'CONFIRMED'
FROM (
    SELECT 1 AS pos, 'Dave Quick' AS name, '101' AS transponder, NULL AS secondary
    UNION ALL SELECT 2, 'Sam Speed', '102', NULL
    UNION ALL SELECT 3, 'Jo Turner', '103', NULL
    UNION ALL SELECT 4, 'Pat Drift', '104', '204'
    UNION ALL SELECT 5, 'Kim Apex', '105', NULL
    UNION ALL SELECT 6, 'Lee Grid', '106', NULL
    UNION ALL SELECT 7, 'Max Lap', '107', '207'
    UNION ALL SELECT 8, 'Nina Pole', '108', NULL
    UNION ALL SELECT 9, 'Ruth Chassis', '109', NULL
    UNION ALL SELECT 10, 'Omar Pinion', '110', NULL
    UNION ALL SELECT 11, 'Tess Droop', '111', NULL
    UNION ALL SELECT 12, 'Ivan Camber', '112', NULL
    UNION ALL SELECT 13, 'Hana Toe', '113', NULL
    UNION ALL SELECT 14, 'Zed Caster', '114', NULL
    UNION ALL SELECT 15, 'Fay Spur', '115', NULL
    UNION ALL SELECT 16, 'Gus Wishbone', '116', NULL
) r
JOIN competitors comp ON comp.display_name = r.name
JOIN events e ON e.name LIKE 'Demo: %'
JOIN event_classes ec ON ec.event_id = e.id
WHERE r.pos <= CASE e.name
    WHEN 'Demo: Timed, rolling start' THEN 8
    WHEN 'Demo: Timed, grid start with finals' THEN 12
    WHEN 'Demo: Bump-up finals' THEN 14
    WHEN 'Demo: Points finals' THEN 12
    WHEN 'Demo: Points finals in legs' THEN 10
    WHEN 'Demo: Shikabane heats and mains' THEN 14
    WHEN 'Demo: Shikabane timed heats' THEN 8
END
ORDER BY e.id, r.pos;
