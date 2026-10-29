package dev.monkeypatch.rctiming.domain.auth;

import dev.monkeypatch.rctiming.persistence.CreatedAt;

import java.time.Instant;

/**
 * One row of {@code passkeys} (#475): a WebAuthn credential an official has registered. Holds the credential id
 * and the public half of the key, both base64url text, and never anything secret: the private key stays on the
 * official's device. See {@link PasskeyRepository}.
 */
public class Passkey implements CreatedAt {

    private Long id;

    private Long userId;

    /** The authenticator's id for this credential, base64url; unique across all officials. */
    private String credentialId;

    /** The random id every credential of this official carries, base64url; never the user id or any name. */
    private String userHandle;

    /** The credential's public key in COSE form, base64url. */
    private String publicKey;

    /** The authenticator's signature counter as last seen; 0 for an authenticator that does not count. */
    private long signCount;

    /** What the official called it, such as "Dave's phone". */
    private String name;

    private Instant createdAt;

    /** When it last signed the official in, or null if it never has. */
    private Instant lastUsedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getUserHandle() { return userHandle; }
    public void setUserHandle(String userHandle) { this.userHandle = userHandle; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
