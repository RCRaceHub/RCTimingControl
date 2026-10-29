package dev.monkeypatch.rctiming.domain.auth;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PasskeysRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Passkeys.PASSKEYS;

/**
 * Repository for {@link Passkey} ({@code passkeys}). Lookups by credential id and user handle serve the sign-in,
 * the rest serve an official's own account page and the admin password reset.
 */
@Repository
public class PasskeyRepository extends JooqRepository<Passkey, PasskeysRecord> {

    private static final PasskeyMapper MAPPER = Mappers.getMapper(PasskeyMapper.class);

    public PasskeyRepository(DSLContext dsl) {
        super(dsl, PASSKEYS, PASSKEYS.ID);
    }

    /** The official's passkeys, oldest first. */
    public List<Passkey> findByUserId(Long userId) {
        return findWhere(PASSKEYS.USER_ID.eq(userId), PASSKEYS.CREATED_AT.asc(), PASSKEYS.ID.asc());
    }

    /** The passkey with this base64url credential id, whoever owns it. */
    public Optional<Passkey> findByCredentialId(String credentialId) {
        return findOne(PASSKEYS.CREDENTIAL_ID.eq(credentialId));
    }

    /** Whether any official has a passkey with this credential id. */
    public boolean existsByCredentialId(String credentialId) {
        return dsl.fetchExists(PASSKEYS, PASSKEYS.CREDENTIAL_ID.eq(credentialId));
    }

    /** The passkeys carrying this user handle (all of one official's), oldest first. */
    public List<Passkey> findByUserHandle(String userHandle) {
        return findWhere(PASSKEYS.USER_HANDLE.eq(userHandle), PASSKEYS.CREATED_AT.asc(), PASSKEYS.ID.asc());
    }

    /** The user handle the official's existing passkeys carry, so a new one reuses it; empty if they have none. */
    public Optional<String> findUserHandle(Long userId) {
        return dsl.select(PASSKEYS.USER_HANDLE).from(PASSKEYS).where(PASSKEYS.USER_ID.eq(userId))
                .orderBy(PASSKEYS.ID.asc()).limit(1).fetchOptional(PASSKEYS.USER_HANDLE);
    }

    /** Notes a successful sign-in: the authenticator's new signature counter and the time. */
    @Transactional
    public void recordUse(Long passkeyId, long signCount, Instant at) {
        dsl.update(PASSKEYS)
                .set(PASSKEYS.SIGN_COUNT, signCount)
                .set(PASSKEYS.LAST_USED_AT, at)
                .where(PASSKEYS.ID.eq(passkeyId))
                .execute();
    }

    /** Deletes one of this official's passkeys. False when they have no such passkey, so nobody can remove another's. */
    @Transactional
    public boolean deleteForUser(Long passkeyId, Long userId) {
        return dsl.deleteFrom(PASSKEYS)
                .where(PASSKEYS.ID.eq(passkeyId).and(PASSKEYS.USER_ID.eq(userId)))
                .execute() == 1;
    }

    /** Deletes every passkey the official has; returns how many went. */
    @Transactional
    public int deleteAllForUser(Long userId) {
        return dsl.deleteFrom(PASSKEYS).where(PASSKEYS.USER_ID.eq(userId)).execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(PASSKEYS.CREATED_AT);
    }

    @Override
    protected Passkey toEntity(PasskeysRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Passkey p, PasskeysRecord r) {
        MAPPER.toRecord(p, r);
    }

    @Override
    protected Long idOf(Passkey p) {
        return p.getId();
    }

    @Override
    protected void setId(Passkey p, Long id) {
        p.setId(id);
    }
}
