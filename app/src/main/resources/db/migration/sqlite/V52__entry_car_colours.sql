-- The colours of an entry's car, for screens that show a colour where there is no car photo (#685).
-- colours is one to four #rrggbb values separated by commas, in the order the official gave them (for example
-- red, yellow and white). Not personal data. A car with no row here shows its class colour instead.
CREATE TABLE entry_car_colours (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id BIGINT NOT NULL,
    colours VARCHAR(40) NOT NULL,
    CONSTRAINT entry_car_colours_entry_id_key UNIQUE (entry_id),
    CONSTRAINT entry_car_colours_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
);
