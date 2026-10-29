-- Racers never sign in to RCTC (#18), so they do not belong in the users table. V2000 made one user per demo racer
-- (no roles) and the Officials page listed them as able to sign in. Their competitors and entries are already made,
-- so the role-less users are deleted. A separate migration rather than an edit to V2000, so a demo database made
-- before this one is cleaned too.
DELETE FROM users WHERE id NOT IN (SELECT user_id FROM user_roles);
