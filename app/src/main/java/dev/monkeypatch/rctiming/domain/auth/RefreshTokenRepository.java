package dev.monkeypatch.rctiming.domain.auth;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RefreshTokensRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RefreshTokens.REFRESH_TOKENS;

/**
 * Repository for {@link RefreshToken} ({@code refresh_tokens}). The revoke and rotate methods are single statements
 * or single write transactions, so they stay correct when two requests present the same token at once.
 */
@Repository
public class RefreshTokenRepository extends JooqRepository<RefreshToken, RefreshTokensRecord> {

    public RefreshTokenRepository(DSLContext dsl) {
        super(dsl, REFRESH_TOKENS, REFRESH_TOKENS.ID);
    }

    /** Finds a token by the hash of its value; revoked and expired tokens are returned too, the caller checks. */
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return findOne(REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash));
    }

    /**
     * Revokes one token if it is still active, in a single statement. Returns false when it was already
     * revoked, so of two requests that present the same token at once, only one gets true. That keeps a
     * refresh token single-use under concurrency, where a read followed by a save would let both through.
     */
    @Transactional
    public boolean revokeIfActive(Long tokenId) {
        return dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED, true)
                .where(REFRESH_TOKENS.ID.eq(tokenId).and(REFRESH_TOKENS.REVOKED.isFalse()))
                .execute() == 1;
    }

    /**
     * Rotates a token: revokes the old one and stores its replacement, in one transaction. False (and nothing
     * stored) when the old one was already revoked. Because this and {@link #revokeFamily} both run as write
     * transactions, which take turns on the single write connection, a sign-out either lands first (and this
     * returns false) or lands after (and revokes the replacement too). A refresh can never slip a live token
     * past a sign-out.
     */
    @Transactional
    public boolean rotate(Long oldTokenId, RefreshToken replacement) {
        if (!revokeIfActive(oldTokenId)) {
            return false;
        }
        save(replacement);
        return true;
    }

    /**
     * Revokes the given token and every other token in its family, so a sign-out ends the whole chain one
     * sign-in has been rotated through. A token with no family (issued before families) is revoked alone.
     * Returns how many tokens were revoked, which is 0 when the sign-in had already ended.
     */
    @Transactional
    public int revokeFamily(RefreshToken token) {
        if (token.getFamilyId() == null) {
            return revokeIfActive(token.getId()) ? 1 : 0;
        }
        return dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED, true)
                .where(REFRESH_TOKENS.FAMILY_ID.eq(token.getFamilyId()).and(REFRESH_TOKENS.REVOKED.isFalse()))
                .execute();
    }

    /**
     * Deletes the tokens past their expiry (#262), revoked or not. A revoked token stays until it expires, so a replay
     * of a rotated token is still tied to its official in the audit log. Returns how many went.
     */
    @Transactional
    public int deleteExpired(Instant now) {
        return dsl.deleteFrom(REFRESH_TOKENS).where(REFRESH_TOKENS.EXPIRES_AT.lt(now)).execute();
    }

    /** Revokes every refresh token the official still holds, signing them out of every browser. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return dsl.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED, true)
                .where(REFRESH_TOKENS.USER_ID.eq(userId).and(REFRESH_TOKENS.REVOKED.isFalse()))
                .execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(REFRESH_TOKENS.CREATED_AT);
    }

    @Override
    protected RefreshToken toEntity(RefreshTokensRecord r) {
        RefreshToken t = new RefreshToken();
        t.setId(r.getId());
        t.setUserId(r.getUserId());
        t.setTokenHash(r.getTokenHash());
        t.setExpiresAt(r.getExpiresAt());
        t.setCreatedAt(r.getCreatedAt());
        t.setRevoked(r.getRevoked());
        t.setFamilyId(r.getFamilyId());
        return t;
    }

    @Override
    protected void toRecord(RefreshToken t, RefreshTokensRecord r) {
        r.setUserId(t.getUserId());
        r.setTokenHash(t.getTokenHash());
        r.setExpiresAt(t.getExpiresAt());
        r.setCreatedAt(t.getCreatedAt());
        r.setRevoked(t.isRevoked());
        r.setFamilyId(t.getFamilyId());
    }

    @Override
    protected Long idOf(RefreshToken t) {
        return t.getId();
    }

    @Override
    protected void setId(RefreshToken t, Long id) {
        t.setId(id);
    }
}
