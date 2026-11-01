-- Screen manager (#708): the EventScreenDisplay viewers (Raspberry Pi screens) this app controls, and the playlists
-- it pushes to them. A screen is keyed by the id it reports (screen_id), never by address, because addresses
-- change; host and port are where it was last seen. api_token is the bearer token the screen asks for on changes
-- (never sent to a browser). pushed_revision and pushed_source are what the manager last set, so a screen changed
-- by someone else can be flagged. playlist_id is the playlist assigned to the screen, kept without a foreign key:
-- deleting a playlist clears it in the service. items is a JSON array of {"url", "duration", "label", "id"}.
CREATE TABLE display_playlists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL,
    default_duration INTEGER NOT NULL DEFAULT 30,
    items TEXT NOT NULL CHECK (json_valid(items)),
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT display_playlists_name_key UNIQUE (name)
);

CREATE TABLE display_screens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    screen_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL DEFAULT 8080,
    agent_version VARCHAR(50),
    api_version VARCHAR(20),
    api_token VARCHAR(255),
    playlist_id BIGINT,
    pushed_revision BIGINT,
    pushed_source VARCHAR(255),
    pushed_at BIGINT,
    last_seen_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT display_screens_screen_id_key UNIQUE (screen_id)
);
