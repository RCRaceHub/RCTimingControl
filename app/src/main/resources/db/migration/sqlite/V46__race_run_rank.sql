-- Finals reordering: where a race runs in its whole event once an official has moved finals around (for example
-- the legs of a three-leg A final between the other finals). Null means the generated order: round, then position
-- in the round. Reordering gives every race of the event a rank at once.
ALTER TABLE races ADD COLUMN run_rank INTEGER;
