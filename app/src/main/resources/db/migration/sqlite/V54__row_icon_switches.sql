-- The row icons the club has switched off on the referee screen, the live race board and the board views (the pace
-- thumbs, flying-lap flame, lap flash, close, lapped, penalty and no-signal markers): a comma-separated list of icon
-- names, empty for none. The streaming overlay keeps its own choice in each overlay theme.
ALTER TABLE club_profiles ADD COLUMN hidden_row_icons TEXT NOT NULL DEFAULT '';
