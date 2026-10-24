-- Category splits for championships (#494).
--
-- competitors.categories: the tags a driver holds (for example Junior, Vet, F-Grade), kept as a comma separated
--   list. Null or empty means none. Set by officials in RCTC only.
-- championships.split_categories: the tags this championship also produces standings for, in the order they are
--   shown, comma separated. Null or empty means no splits, so the standings are the per-class tables (and the
--   overall table where that is on) as before.
ALTER TABLE competitors ADD COLUMN categories TEXT;
ALTER TABLE championships ADD COLUMN split_categories TEXT;
