-- A refused sign-in no longer keeps the email that was typed (#264): it may belong to someone who is not an official.
-- Rows written before this change are scrubbed the same way. The official the attempt was against, when there was
-- one, is still in entity_id. This replaces V18's note that actor_label holds "anonymous:<email tried>": it now
-- holds "anonymous:known account" or "anonymous:unknown email", never an address.
UPDATE audit_log
SET actor_label = CASE WHEN entity_id IS NOT NULL THEN 'anonymous:known account' ELSE 'anonymous:unknown email' END
WHERE action = 'LOGIN_FAILED'
  AND actor_label LIKE 'anonymous:%';
