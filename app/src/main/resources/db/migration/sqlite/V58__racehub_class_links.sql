-- Links each class of an imported file to one of the club's racing classes, once for the whole club. Without it,
-- every meeting needed its own class mapping. class_key is the same key racehub_class_mappings uses: RaceHub's event
-- class id, or "<source>:<id>" for another system. Deleting the racing class removes its links.
CREATE TABLE racehub_class_links (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    class_key VARCHAR(150) NOT NULL,
    racing_class_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT uq_racehub_class_links UNIQUE (class_key),
    CONSTRAINT racehub_class_links_racing_class_id_fkey FOREIGN KEY (racing_class_id) REFERENCES racing_classes(id) ON DELETE CASCADE
);
CREATE INDEX idx_racehub_class_links_racing_class_id ON racehub_class_links (racing_class_id);
