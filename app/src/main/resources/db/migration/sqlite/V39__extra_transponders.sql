-- An entry can race on up to four transponders (#482): the primary and secondary it already had, plus a third and
-- a fourth. Laps from any of them count for the entry. Null means none.
ALTER TABLE entries ADD COLUMN third_transponder_number VARCHAR(20);
ALTER TABLE entries ADD COLUMN fourth_transponder_number VARCHAR(20);
