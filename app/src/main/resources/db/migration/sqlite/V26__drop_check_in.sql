-- Drops check-in (the check-in desk is gone): entries.checked_in_at and entries.checked_in_by_user_id.
--
-- checked_in_by_user_id is in a foreign key, which SQLite cannot drop a column from, so entries is rebuilt as V21
-- rebuilt it: foreign keys off, copy into a new table, drop the old one and rename the new one. Foreign keys can only
-- be turned off outside a transaction, so this script runs outside Flyway's (V26__drop_check_in.sql.conf) and wraps
-- the work in a savepoint of its own: Flyway reads a bare BEGIN as the start of a block.
PRAGMA foreign_keys = OFF;

SAVEPOINT drop_check_in;

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
    racehub_event_class_id VARCHAR(100),
    imported_transponder_number VARCHAR(20),
    imported_secondary_transponder_number VARCHAR(20),
    CONSTRAINT entries_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'WITHDRAWN')),
    CONSTRAINT entries_competitor_id_fkey FOREIGN KEY (competitor_id) REFERENCES competitors(id),
    CONSTRAINT entries_event_class_id_fkey FOREIGN KEY (event_class_id) REFERENCES event_classes(id) ON DELETE SET NULL,
    CONSTRAINT entries_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id)
);

INSERT INTO entries_new (id, event_id, event_class_id, transponder_number, status, submitted_at, updated_at,
                         confirmed_at, withdrawn_at, competitor_id, secondary_transponder_number, external_source,
                         external_entry_id, external_entry_version, racehub_arrival, racehub_event_class_id,
                         imported_transponder_number, imported_secondary_transponder_number)
SELECT id, event_id, event_class_id, transponder_number, status, submitted_at, updated_at,
       confirmed_at, withdrawn_at, competitor_id, secondary_transponder_number, external_source,
       external_entry_id, external_entry_version, racehub_arrival, racehub_event_class_id,
       imported_transponder_number, imported_secondary_transponder_number
FROM entries;

DELETE FROM sqlite_sequence WHERE name = 'entries_new';
INSERT INTO sqlite_sequence (name, seq) SELECT 'entries_new', seq FROM sqlite_sequence WHERE name = 'entries';

DROP TABLE entries;
ALTER TABLE entries_new RENAME TO entries;

CREATE INDEX idx_entries_competitor_id ON entries (competitor_id);
CREATE INDEX idx_entries_event_id ON entries (event_id);
CREATE UNIQUE INDEX idx_entries_external ON entries (external_source, external_entry_id) WHERE external_entry_id IS NOT NULL;
CREATE UNIQUE INDEX idx_entries_no_duplicate ON entries (competitor_id, event_id, event_class_id) WHERE status <> 'WITHDRAWN';

RELEASE drop_check_in;

PRAGMA foreign_keys = ON;
