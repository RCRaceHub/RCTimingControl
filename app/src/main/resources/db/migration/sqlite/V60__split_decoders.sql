-- Extra decoders for split loops (#672). The club's main decoder (club_profiles.decoder_*) stays the finish loop and
-- the only one that credits laps. Each row here is a decoder at a split point on the track: its passings never credit
-- a lap, they give sector times. split_number is the order of the split round the lap, 1 for the first after the
-- start/finish loop. clock_offset_ms corrects the server-anchored clock of an RC-4 decoder, which has no absolute time.
CREATE TABLE split_decoders (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL,
    host            TEXT    NOT NULL,
    port            INTEGER NOT NULL DEFAULT 5100,
    split_number    INTEGER NOT NULL,
    clock_offset_ms INTEGER NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT 1,
    CHECK (port BETWEEN 1 AND 65535),
    CHECK (split_number BETWEEN 1 AND 9),
    CHECK (enabled IN (0, 1)),
    UNIQUE (split_number)
);
