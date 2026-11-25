-- Feature switches (#795, #796). A module is a built-in feature an admin can switch off for the whole club. A row exists
-- only once a switch has been flipped; a module with no row is on. changed_by is the official who flipped it (null when
-- the system did). event_modules_off lets one event turn a module off even though the club has it on (a fun day, say);
-- an event can never turn on a module the club has off, so there is no "on" row.
CREATE TABLE modules (
    id VARCHAR(50) PRIMARY KEY,
    enabled INTEGER NOT NULL,
    changed_by BIGINT,
    changed_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT modules_enabled_check CHECK (enabled IN (0, 1))
);
CREATE TABLE event_modules_off (
    event_id BIGINT NOT NULL,
    module_id VARCHAR(50) NOT NULL,
    PRIMARY KEY (event_id, module_id),
    CONSTRAINT event_modules_off_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);
