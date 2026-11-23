-- Links a race format of the club's RaceHub (its id, as the entry export's event.race_format_id carries it) to one of
-- RCTC's own format templates, once for the whole club. A meeting imported with a linked format adds its classes with
-- that template. Deleting the template removes its links, so a meeting then falls back to asking the official.
CREATE TABLE racehub_format_links (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    format_key VARCHAR(150) NOT NULL,
    template_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    CONSTRAINT uq_racehub_format_links UNIQUE (format_key),
    CONSTRAINT racehub_format_links_template_id_fkey FOREIGN KEY (template_id) REFERENCES race_format_templates(id) ON DELETE CASCADE
);
CREATE INDEX idx_racehub_format_links_template_id ON racehub_format_links (template_id);
