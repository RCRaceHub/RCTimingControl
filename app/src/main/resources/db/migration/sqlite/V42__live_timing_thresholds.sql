-- The club's live timing thresholds (#505), all in milliseconds except the lap count. One set for the club.
-- battle_gap_ms (V41) is the largest gap that makes an on-track battle.
-- battle_trend_ms: a gap changing by more than this each lap is closing or growing rather than holding.
-- battle_fast_ms: a gap closing by this much or more each lap marks a car gaining fast.
-- battle_hold_laps: laps a pair stays a battle after it drops outside the gap.
-- close_gap_ms: how near the car ahead is shown as close (amber row); 0 switches the tint off.
-- late_margin_ms: how far past its average lap a car is before its lap bar turns red.
ALTER TABLE club_profiles ADD COLUMN battle_trend_ms INTEGER NOT NULL DEFAULT 150;
ALTER TABLE club_profiles ADD COLUMN battle_fast_ms INTEGER NOT NULL DEFAULT 300;
ALTER TABLE club_profiles ADD COLUMN battle_hold_laps INTEGER NOT NULL DEFAULT 2;
ALTER TABLE club_profiles ADD COLUMN close_gap_ms INTEGER NOT NULL DEFAULT 1000;
ALTER TABLE club_profiles ADD COLUMN late_margin_ms INTEGER NOT NULL DEFAULT 1000;
