-- Photos and driver sponsors for the boards and the commentator view (#520). All optional.
--   competitor_media: one row per competitor. headshot_url is the address of the headshot in object storage;
--     sponsors is the driver's own sponsors, one name per line. A headshot is personal data: it is removed when
--     the competitor is anonymised, and a public screen never shows it for a competitor whose name is not shown.
--   entry_photos: one row per entry, the photo of that entry's car for the event.
CREATE TABLE competitor_media (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    competitor_id BIGINT NOT NULL,
    headshot_url VARCHAR(500),
    sponsors TEXT NOT NULL DEFAULT '',
    CONSTRAINT competitor_media_competitor_id_key UNIQUE (competitor_id),
    CONSTRAINT competitor_media_competitor_id_fkey FOREIGN KEY (competitor_id) REFERENCES competitors(id) ON DELETE CASCADE
);

CREATE TABLE entry_photos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id BIGINT NOT NULL,
    car_photo_url VARCHAR(500) NOT NULL,
    CONSTRAINT entry_photos_entry_id_key UNIQUE (entry_id),
    CONSTRAINT entry_photos_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
);
