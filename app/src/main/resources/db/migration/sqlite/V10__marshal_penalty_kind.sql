-- A marshal penalty is either the loss of the driver's best qualifying round or a lap off their finals result.
-- Penalties given before this were only records, so they take the first kind.
ALTER TABLE marshal_penalties ADD COLUMN kind TEXT NOT NULL DEFAULT 'QUALIFYING_SCORE'
    CHECK (kind IN ('QUALIFYING_SCORE', 'FINALS_LAP'));
