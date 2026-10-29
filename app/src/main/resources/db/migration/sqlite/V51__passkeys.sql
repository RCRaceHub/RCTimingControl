-- Passkeys (#475): the WebAuthn credentials an official has registered, so they can sign in with a fingerprint,
-- face or security key instead of a password. Only the credential id and the public half of the key are stored;
-- the private key never leaves the official's device. credential_id and public_key are base64url text.
-- user_handle is a random id the device keeps with the credential and hands back at sign-in, so the official is
-- found without typing an email; every credential of one official carries the same handle. A passkey has no
-- meaning without its official, so they go together (and an admin password reset deletes them, in the service).
CREATE TABLE passkeys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    credential_id VARCHAR(1024) NOT NULL,
    user_handle VARCHAR(128) NOT NULL,
    public_key VARCHAR(2048) NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(80) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    last_used_at BIGINT,
    CONSTRAINT passkeys_credential_id_key UNIQUE (credential_id),
    CONSTRAINT passkeys_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_passkeys_user_id ON passkeys (user_id);
CREATE INDEX idx_passkeys_user_handle ON passkeys (user_handle);
