-- Wyvern RC Club demo data for the Docker trial stack and the e2e suite.
-- Loaded by Flyway when the app runs with the 'demo' profile (db/demo/<vendor>), after the
-- baseline migrations, so it runs exactly once against a fresh database.
-- Password for every account is 'trial123' (BCrypt, cost 10).

-- 1. governing body
INSERT INTO governing_body_affiliations (code, display_name, membership_required) VALUES ('BRCA', 'British Radio Car Association', 0);

-- 2. users. Only officials sign in (L10, #18); dave.racer is kept so the e2e suite can check a racer is turned away.
INSERT INTO users (email, password_hash, first_name, last_name) VALUES
    ('admin@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Alice', 'Admin'),
    ('dave.racer@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Dave', 'Quick'),
    ('sam.speed@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Sam', 'Speed'),
    ('jo.turner@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Jo', 'Turner'),
    ('pat.drift@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Pat', 'Drift'),
    ('kim.apex@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Kim', 'Apex'),
    ('lee.grid@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Lee', 'Grid'),
    ('max.lap@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Max', 'Lap'),
    ('nina.pole@example.com', '$2b$10$O1DvFrcjL3XLlNnXWdaa1.vynY1S5eZ2eLwvJ9NuA2jbxLcLO4y52', 'Nina', 'Pole');

INSERT INTO user_roles (user_id, role)
SELECT u.id, r.role FROM users u, (SELECT 'ADMIN' AS role UNION ALL SELECT 'RACE_DIRECTOR' UNION ALL SELECT 'REFEREE') r
WHERE u.email = 'admin@example.com';

-- 3. club
INSERT INTO club_profiles (name, email, timezone, decoder_host, decoder_port, decoder_protocol)
VALUES ('Wyvern RC Club', 'info@example.com', 'Europe/London', 'fake-decoder', 5100, 'RC4');

-- 4. tracks and decoder loops
INSERT INTO tracks (name, track_length) VALUES ('Rivermead Circuit', 220), ('Parklands Arena', 185);
INSERT INTO decoder_loops (track_id, loop_id, display_name, loop_type, is_scoring_loop)
SELECT id, 'L1', 'Start/Finish', 'FINISH_LINE', 1 FROM tracks WHERE name IN ('Rivermead Circuit', 'Parklands Arena');

-- 5. racing classes
INSERT INTO racing_classes (name, description) VALUES
    ('13.5 Touring', 'Spec 13.5T brushless touring car class'),
    ('Stock Buggy', 'Stock-spec 2WD/4WD buggy class'),
    ('F1 Open', 'Open formula 1/10 scale F1 class');

-- 6. race format templates
INSERT INTO race_format_templates (name, config) VALUES
    ('Timed 5-min', '{"type":"TIMED","durationMinutes":5,"startType":"ROLLING","qualifyingType":"FASTEST_LAP","racePaddingMinutes":1,"staggerIntervalSeconds":5}'),
    ('Bump-up Finals', '{"type":"BUMP_UP","qualifyingHeats":3,"heatDurationMinutes":5,"bestHeatsCount":2,"gridSize":8,"bumpSpots":2,"qualifyingStartType":"STAGGER","finalsStartType":"GRID","qualifyingType":"FTQ","racePaddingMinutes":2,"staggerIntervalSeconds":5}'),
    ('Points Finals', '{"type":"POINTS_FINALS","qualifyingHeats":3,"finalsCount":3,"finalDurationMinutes":5,"heatDurationMinutes":5,"qualifyingStartType":"STAGGER","finalsStartType":"GRID","qualifyingType":"FTQ","racePaddingMinutes":2,"staggerIntervalSeconds":5}');

-- 7. events: Round 4 is open for entries, Round 3 is completed
INSERT INTO events (name, event_date, status, track_id) VALUES
    ('Wyvern Winter Series Round 4', '2026-06-20', 'OPEN', (SELECT id FROM tracks WHERE name = 'Rivermead Circuit')),
    ('Wyvern Winter Series Round 3', '2026-05-09', 'COMPLETED', (SELECT id FROM tracks WHERE name = 'Parklands Arena'));

-- 8. event classes
INSERT INTO event_classes (config_snapshot, template_id, event_id, racing_class_id, finals_count, cars_per_final, bump_count)
SELECT (SELECT config FROM race_format_templates WHERE name = 'Timed 5-min'), (SELECT id FROM race_format_templates WHERE name = 'Timed 5-min'), e.id, (SELECT id FROM racing_classes WHERE name = '13.5 Touring'), 1, 8, 0
FROM events e WHERE e.name IN ('Wyvern Winter Series Round 4', 'Wyvern Winter Series Round 3') ORDER BY e.id;

-- 9. competitors, one per racer
INSERT INTO competitors (display_name)
SELECT first_name || ' ' || last_name FROM users WHERE email <> 'admin@example.com' ORDER BY id;

-- 10. entries for both events, keyed on competitor
INSERT INTO entries (competitor_id, event_id, event_class_id, transponder_number, status)
SELECT c.id, e.id, ec.id, t.transponder, 'CONFIRMED'
FROM (
    SELECT 'dave.racer@example.com' AS email, '101' AS transponder
    UNION ALL SELECT 'sam.speed@example.com' AS email, '102' AS transponder
    UNION ALL SELECT 'jo.turner@example.com' AS email, '103' AS transponder
    UNION ALL SELECT 'pat.drift@example.com' AS email, '104' AS transponder
    UNION ALL SELECT 'kim.apex@example.com' AS email, '105' AS transponder
    UNION ALL SELECT 'lee.grid@example.com' AS email, '106' AS transponder
    UNION ALL SELECT 'max.lap@example.com' AS email, '107' AS transponder
    UNION ALL SELECT 'nina.pole@example.com' AS email, '108' AS transponder
) t
JOIN users u ON u.email = t.email
JOIN competitors c ON c.display_name = u.first_name || ' ' || u.last_name
JOIN events e ON e.name IN ('Wyvern Winter Series Round 4', 'Wyvern Winter Series Round 3')
JOIN event_classes ec ON ec.event_id = e.id
ORDER BY e.id, t.transponder;

-- 11. championship
INSERT INTO championships (name, best_x_from_y_x, best_x_from_y_y, scoring_source, tq_bonus_points, afinal_winner_bonus_points)
VALUES ('2026 Wyvern Winter Series', 4, 6, 'FINALS', 2, 3);
INSERT INTO championship_classes (championship_id, racing_class_id) VALUES ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), (SELECT id FROM racing_classes WHERE name = '13.5 Touring'));
INSERT INTO championship_event_links (championship_id, event_id, round_number) VALUES ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), (SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 3'), 3), ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), (SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 4'), 4);
INSERT INTO championship_points_scale (championship_id, position, points) VALUES
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 1, 15),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 2, 12),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 3, 10),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 4, 8),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 5, 6),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 6, 5),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 7, 4),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 8, 3),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 9, 2),
    ((SELECT id FROM championships WHERE name = '2026 Wyvern Winter Series'), 10, 1);

-- 12. Round 3 A-final: round, race, grid and result
INSERT INTO rounds (event_id, type, round_number, sequence_in_event, status) VALUES ((SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 3'), 'FINAL', 1, 1, 'COMPLETED');
INSERT INTO races (round_id, event_class_id, heat_number, sequence_in_round, final_letter, start_type, status, started_at, finished_at)
SELECT r.id, ec.id, 1, 1, 'A', 'GRID', 'FINISHED', CAST(unixepoch('2026-05-09 13:00:00') * 1000000 AS INTEGER), CAST(unixepoch('2026-05-09 13:05:30') * 1000000 AS INTEGER)
FROM rounds r JOIN event_classes ec ON ec.event_id = r.event_id WHERE r.event_id = (SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 3');
INSERT INTO race_entries (race_id, entry_id, grid_position, car_number)
SELECT ra.id, en.id, CAST(en.transponder_number AS INTEGER) - 100, CAST(en.transponder_number AS INTEGER) - 100
FROM races ra JOIN rounds r ON r.id = ra.round_id JOIN entries en ON en.event_id = r.event_id WHERE r.event_id = (SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 3');
-- The snapshot has the shape ResultSnapshotService writes: one row per entry, keyed by entry id
INSERT INTO result_snapshots (race_id, finished_at, positions_json, lap_history_json)
SELECT ra.id, CAST(unixepoch('2026-05-09 13:05:30') * 1000000 AS INTEGER),
       (SELECT json_group_array(json(row)) FROM (SELECT json_object(
                   'position', f.position, 'entryId', en.id, 'competitorId', c.id, 'driverName', c.display_name,
                   'carNumber', CAST(re.car_number AS TEXT), 'lapsCompleted', f.laps, 'totalTimeMs', f.total_ms,
                   'bestLapMs', f.best_ms, 'gapToLeaderMs', f.gap_ms) AS row
        FROM (SELECT * FROM (
                  SELECT 1 AS position, '102' AS transponder, 14 AS laps, 326400 AS total_ms, 22810 AS best_ms, NULL AS gap_ms
                  UNION ALL SELECT 2, '108', 14, 329150, 22940, 2750
                  UNION ALL SELECT 3, '101', 13, 318900, 23480, NULL
                  UNION ALL SELECT 4, '105', 13, 324700, 23720, NULL
                  UNION ALL SELECT 5, '103', 12, 312300, 24610, NULL
                  UNION ALL SELECT 6, '104', 12, 321800, 24900, NULL
                  UNION ALL SELECT 7, '107', 11, 309500, 26120, NULL
                  UNION ALL SELECT 8, '106', 11, 318200, 26480, NULL)) f
        JOIN entries en ON en.event_id = r.event_id AND en.transponder_number = f.transponder
        JOIN competitors c ON c.id = en.competitor_id
        JOIN race_entries re ON re.race_id = ra.id AND re.entry_id = en.id
        ORDER BY f.position)),
       '[]'
FROM races ra JOIN rounds r ON r.id = ra.round_id
WHERE r.event_id = (SELECT id FROM events WHERE name = 'Wyvern Winter Series Round 3');
