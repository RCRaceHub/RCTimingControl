-- Which finals are run in legs when the finals are calculated: only the A final (nationals), or every final.
-- events.final_legs_scope: A_ONLY or ALL. The number of legs and how many count come from the class's format.
--   Changing it affects finals calculated afterwards; finals already calculated keep the legs they have.
ALTER TABLE events ADD COLUMN final_legs_scope VARCHAR(10) NOT NULL DEFAULT 'A_ONLY'
    CONSTRAINT events_final_legs_scope_check CHECK (final_legs_scope IN ('A_ONLY', 'ALL'));
