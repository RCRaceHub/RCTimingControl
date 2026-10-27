-- Drops the two Wyvern Winter Series rounds from V2000 (Round 3 completed, Round 4 open); the 'Demo: ...' events
-- from V2002 replace them. A separate migration rather than an edit to V2000, so a demo database made before this
-- one loses them too. The championship stays, now with no rounds linked.
DELETE FROM result_snapshots WHERE race_id IN (SELECT ra.id FROM races ra JOIN rounds r ON r.id = ra.round_id JOIN events e ON e.id = r.event_id WHERE e.name LIKE 'Wyvern Winter Series Round %');
DELETE FROM race_entries WHERE race_id IN (SELECT ra.id FROM races ra JOIN rounds r ON r.id = ra.round_id JOIN events e ON e.id = r.event_id WHERE e.name LIKE 'Wyvern Winter Series Round %');
DELETE FROM races WHERE round_id IN (SELECT r.id FROM rounds r JOIN events e ON e.id = r.event_id WHERE e.name LIKE 'Wyvern Winter Series Round %');
DELETE FROM rounds WHERE event_id IN (SELECT id FROM events WHERE name LIKE 'Wyvern Winter Series Round %');
DELETE FROM championship_event_links WHERE event_id IN (SELECT id FROM events WHERE name LIKE 'Wyvern Winter Series Round %');
DELETE FROM entries WHERE event_id IN (SELECT id FROM events WHERE name LIKE 'Wyvern Winter Series Round %');
DELETE FROM event_classes WHERE event_id IN (SELECT id FROM events WHERE name LIKE 'Wyvern Winter Series Round %');
DELETE FROM events WHERE name LIKE 'Wyvern Winter Series Round %';
