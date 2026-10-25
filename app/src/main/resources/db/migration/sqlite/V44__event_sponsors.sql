-- Event sponsors (#518): the sponsors of an event, shown in the overlay's sponsor bar. The name is always set; the logo
-- is optional and is kept in object storage, logo_url being the address it is served from. sort_order is the order
-- they are shown in. Entered in RCTC; a RaceHub import may fill the list later.
CREATE TABLE event_sponsors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT event_sponsors_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);
CREATE INDEX idx_event_sponsors_event_id ON event_sponsors (event_id);
