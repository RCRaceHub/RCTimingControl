-- Championship overall table.
--
-- championships.show_overall_table: when 1, the standings also have an "Overall" table that ranks every driver of
--   every class in the championship together on championship points (a 2WD win and a 4WD win score the same),
--   besides each class's own table. Off for existing championships.
ALTER TABLE championships ADD COLUMN show_overall_table BOOLEAN NOT NULL DEFAULT 0 CHECK (show_overall_table IN (0, 1));
