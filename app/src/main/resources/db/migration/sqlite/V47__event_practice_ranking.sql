-- How the live top board ranks drivers in practice: by fastest lap, or by the best 2 or 3 consecutive laps added together.
-- events.practice_ranking: FASTEST, BEST_2 or BEST_3. Chosen per event by race control; applies to the round table
--   (one practice session) and the overall table (each driver's best across the event's practice).
ALTER TABLE events ADD COLUMN practice_ranking VARCHAR(10) NOT NULL DEFAULT 'FASTEST'
    CONSTRAINT events_practice_ranking_check CHECK (practice_ranking IN ('FASTEST', 'BEST_2', 'BEST_3'));
