-- Messages and banners on the display screens (#716): one row (id 1) holding, as JSON, the officials' saved message
-- presets and the two opt-in hooks into the event flow (a hold when qualifying ends, a "finals are up" announcement
-- when the finals are published). No row means the built-in presets and both hooks off.
CREATE TABLE display_message_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    settings TEXT NOT NULL CHECK (json_valid(settings)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER))
);
