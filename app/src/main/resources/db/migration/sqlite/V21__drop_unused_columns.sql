-- Drops schema nothing uses any more (#141):
--   unknown_transponder_links: the first table for linking an unknown transponder. Links are kept in
--     unknown_transponder_link (singular), which stays.
--   races.format_id, races.format_overrides: a race takes its format from its class.
--   entries.user_id, entries.transponder_label: racer logins and their transponder labels went with the racer portal.
--   events.entry_opens_at, events.entry_closes_at: RaceHub runs entry windows.
--   club_profiles.logo, club_profiles.logo_type: the logo is a file on disk, found through logo_url.
--
-- SQLite cannot drop a column that is in a foreign key or an index, so races and entries are rebuilt as V20 rebuilt
-- races: foreign keys off, copy into a new table, drop the old one and rename the new one. Foreign keys can only be
-- turned off outside a transaction, so this script runs outside Flyway's (V21__drop_unused_columns.sql.conf) and wraps
-- the work in a savepoint of its own: Flyway reads a bare BEGIN as the start of a block.
PRAGMA foreign_keys = OFF;

SAVEPOINT drop_unused_columns;

DROP TABLE unknown_transponder_links;

CREATE TABLE races_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    round_id BIGINT NOT NULL,
    event_class_id BIGINT NOT NULL,
    heat_number INTEGER NOT NULL,
    sequence_in_round INTEGER NOT NULL,
    final_letter VARCHAR(5),
    start_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at BIGINT,
    finished_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    abandoned_at BIGINT,
    bump_slots INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT races_start_type_check CHECK (start_type IN ('STAGGER', 'GRID', 'ROLLING')),
    CONSTRAINT races_status_check CHECK (status IN ('PENDING', 'GRID', 'RUNNING', 'STOPPED', 'FINISHED')),
    CONSTRAINT races_event_class_id_fkey FOREIGN KEY (event_class_id) REFERENCES event_classes(id),
    CONSTRAINT races_round_id_fkey FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE
);

INSERT INTO races_new (id, round_id, event_class_id, heat_number, sequence_in_round, final_letter, start_type,
                       status, started_at, finished_at, created_at, updated_at, abandoned_at, bump_slots)
SELECT id, round_id, event_class_id, heat_number, sequence_in_round, final_letter, start_type,
       status, started_at, finished_at, created_at, updated_at, abandoned_at, bump_slots
FROM races;

-- Carry the id counter across, so the id of a race that was deleted is never handed out again
DELETE FROM sqlite_sequence WHERE name = 'races_new';
INSERT INTO sqlite_sequence (name, seq) SELECT 'races_new', seq FROM sqlite_sequence WHERE name = 'races';

DROP TABLE races;
ALTER TABLE races_new RENAME TO races;

CREATE INDEX idx_races_event_class_id ON races (event_class_id);
CREATE INDEX idx_races_round_id ON races (round_id);

CREATE TABLE entries_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id BIGINT NOT NULL,
    event_class_id BIGINT,
    transponder_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    confirmed_at BIGINT,
    withdrawn_at BIGINT,
    competitor_id BIGINT,
    secondary_transponder_number VARCHAR(20),
    external_source VARCHAR(30),
    external_entry_id VARCHAR(100),
    external_entry_version BIGINT,
    racehub_arrival VARCHAR(20),
    checked_in_at BIGINT,
    checked_in_by_user_id BIGINT,
    racehub_event_class_id VARCHAR(100),
    imported_transponder_number VARCHAR(20),
    imported_secondary_transponder_number VARCHAR(20),
    CONSTRAINT entries_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'WITHDRAWN')),
    CONSTRAINT entries_checked_in_by_user_id_fkey FOREIGN KEY (checked_in_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT entries_competitor_id_fkey FOREIGN KEY (competitor_id) REFERENCES competitors(id),
    CONSTRAINT entries_event_class_id_fkey FOREIGN KEY (event_class_id) REFERENCES event_classes(id) ON DELETE SET NULL,
    CONSTRAINT entries_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id)
);

INSERT INTO entries_new (id, event_id, event_class_id, transponder_number, status, submitted_at, updated_at,
                         confirmed_at, withdrawn_at, competitor_id, secondary_transponder_number, external_source,
                         external_entry_id, external_entry_version, racehub_arrival, checked_in_at,
                         checked_in_by_user_id, racehub_event_class_id, imported_transponder_number,
                         imported_secondary_transponder_number)
SELECT id, event_id, event_class_id, transponder_number, status, submitted_at, updated_at,
       confirmed_at, withdrawn_at, competitor_id, secondary_transponder_number, external_source,
       external_entry_id, external_entry_version, racehub_arrival, checked_in_at,
       checked_in_by_user_id, racehub_event_class_id, imported_transponder_number,
       imported_secondary_transponder_number
FROM entries;

DELETE FROM sqlite_sequence WHERE name = 'entries_new';
INSERT INTO sqlite_sequence (name, seq) SELECT 'entries_new', seq FROM sqlite_sequence WHERE name = 'entries';

DROP TABLE entries;
ALTER TABLE entries_new RENAME TO entries;

CREATE INDEX idx_entries_competitor_id ON entries (competitor_id);
CREATE INDEX idx_entries_event_id ON entries (event_id);
CREATE UNIQUE INDEX idx_entries_external ON entries (external_source, external_entry_id) WHERE external_entry_id IS NOT NULL;
CREATE UNIQUE INDEX idx_entries_no_duplicate ON entries (competitor_id, event_id, event_class_id) WHERE status <> 'WITHDRAWN';

-- These columns are in no index, foreign key, CHECK or trigger, so SQLite can drop them in place
ALTER TABLE events DROP COLUMN entry_opens_at;
ALTER TABLE events DROP COLUMN entry_closes_at;

ALTER TABLE club_profiles DROP COLUMN logo;
ALTER TABLE club_profiles DROP COLUMN logo_type;

RELEASE drop_unused_columns;

PRAGMA foreign_keys = ON;
