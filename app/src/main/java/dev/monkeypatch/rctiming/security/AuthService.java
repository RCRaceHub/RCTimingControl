package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.auth.RefreshToken;
import dev.monkeypatch.rctiming.domain.auth.RefreshTokenRepository;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Officials' sign-in sessions: signing in, swapping a refresh token for a new access token, and signing out.
 * Each refusal is recorded in the audit log; the controller only turns the outcome into a response and a cookie.
 *
 * <p>Not transactional as a whole: the password check is slow on purpose and must not hold the single write
 * connection, so each method opens a transaction only around its writes.
 */
@Service
public class AuthService {

    /** Why a sign-in or refresh was refused, with the wording the audit row uses. */
    public enum Refusal {
        WRONG_CREDENTIALS("wrong email or password"),
        NOT_AN_OFFICIAL("not an official"),
        DISABLED("account disabled"),
        UNKNOWN_TOKEN("unknown token"),
        TOKEN_USED("token already used or revoked"),
        TOKEN_EXPIRED("token expired"),
        NO_SUCH_OFFICIAL("no such official");

        private final String reason;

        Refusal(String reason) {
            this.reason = reason;
        }
    }

    /** The outcome of a sign-in or a refresh. */
    public sealed interface Outcome {
        /** Signed in: the official, their access token and the raw refresh token for the cookie. */
        record SignedIn(User user, String accessToken, String refreshToken) implements Outcome {
            /** Leaves out the user (with its password hash) and both tokens, in case an outcome is ever logged. */
            @Override
            public String toString() {
                return "SignedIn[userId=" + user.getId() + "]";
            }
        }

        record Refused(Refusal refusal) implements Outcome {}
    }

    /**
     * Checked against when no official has the email, so an unknown email takes as long to refuse as a wrong
     * password and the timing does not tell anyone which emails belong to officials.
     */
    private final String unknownEmailHash;

    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public AuthService(UserService userService,
                       JwtTokenService jwtTokenService,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService audit,
                       TransactionTemplate transactions) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.transactions = transactions;
        this.unknownEmailHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Signs in. Every attempt is recorded in the audit log, wrong passwords included. */
    public Outcome login(String email, String password) {
        Optional<User> userOpt = userService.findByEmail(email);
        String passwordHash = userOpt.map(User::getPasswordHash).orElse(unknownEmailHash);
        if (!passwordEncoder.matches(password, passwordHash) || userOpt.isEmpty()) {
            // Never say which field is wrong (credential enumeration prevention). The audit row is just as
            // vague about it: only an admin reads it, but it names no password.
            return refuseLogin(email, userOpt.map(User::getId).orElse(null), Refusal.WRONG_CREDENTIALS);
        }
        User user = userOpt.get();
        if (!user.isOfficial()) {
            // Only race officials sign in (L10, #18)
            return refuseLogin(email, user.getId(), Refusal.NOT_AN_OFFICIAL);
        }
        if (!user.isEnabled()) {
            // An admin has disabled this official (#61)
            return refuseLogin(email, user.getId(), Refusal.DISABLED);
        }
        String accessToken = jwtTokenService.generateAccessToken(user);
        // A new sign-in starts a new family of refresh tokens. The token and the audit row are written in one
        // transaction, so there is no session without its record.
        NewRefreshToken refreshToken = newRefreshToken(user, UUID.randomUUID().toString());
        transactions.executeWithoutResult(status -> {
            refreshTokenRepository.save(refreshToken.entity());
            audit.entry(Actor.official(user.getId()), "LOGIN_SUCCEEDED")
                    .entity("official", user.getId())
                    .summary("Signed in")
                    .record();
        });
        return new Outcome.SignedIn(user, accessToken, refreshToken.rawValue());
    }

    /**
     * Swaps a refresh token for a new access token and a new refresh token. Successful refreshes are not
     * recorded (every page load and every 15 minutes would add one); a refused one is, because a token that is
     * unknown, expired or already used can mean a stolen or replayed cookie.
     */
    public Outcome refresh(String rawToken) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(sha256Hex(rawToken));
        if (tokenOpt.isEmpty()) {
            return refuseRefresh(null, Refusal.UNKNOWN_TOKEN);
        }

        RefreshToken oldToken = tokenOpt.get();
        if (oldToken.isRevoked()) {
            return refuseRefresh(oldToken.getUserId(), Refusal.TOKEN_USED);
        }
        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            return refuseRefresh(oldToken.getUserId(), Refusal.TOKEN_EXPIRED);
        }

        User user = userService.findById(oldToken.getUserId()).orElse(null);
        if (user == null) {
            return refuseRefresh(oldToken.getUserId(), Refusal.NO_SUCH_OFFICIAL);
        }
        if (!user.isOfficial()) {
            return refuseRefresh(user.getId(), Refusal.NOT_AN_OFFICIAL);
        }
        if (!user.isEnabled()) {
            // Disabling revokes the tokens too (#61); this covers one issued in between
            return refuseRefresh(user.getId(), Refusal.DISABLED);
        }

        // Token rotation: revoke the old token and store its replacement in the same family, in one
        // transaction. The old token is revoked with a compare-and-set, so a token used by two requests at
        // once (a double click, two tabs) is accepted for only one of them, and a sign-out that got in first
        // makes this fail rather than leaving a live token behind it.
        String familyId = oldToken.getFamilyId() != null ? oldToken.getFamilyId() : UUID.randomUUID().toString();
        NewRefreshToken replacement = newRefreshToken(user, familyId);
        if (!refreshTokenRepository.rotate(oldToken.getId(), replacement.entity())) {
            return refuseRefresh(user.getId(), Refusal.TOKEN_USED);
        }
        return new Outcome.SignedIn(user, jwtTokenService.generateAccessToken(user), replacement.rawValue());
    }

    /**
     * Signs out the browser holding this refresh token by revoking the token's whole family: a refresh that
     * rotated it a moment earlier (the browser's cookie may be one rotation behind) has already issued a
     * replacement, and that is revoked too. A refresh racing this call either lands first and is revoked here,
     * or lands second and is refused (see {@link RefreshTokenRepository#rotate}). An unknown or already revoked
     * token does nothing.
     */
    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)).ifPresent(token -> {
            // Only a sign-in that was still live is recorded, so a repeated sign-out adds nothing
            if (refreshTokenRepository.revokeFamily(token) > 0) {
                audit.entry(Actor.official(token.getUserId()), "LOGOUT")
                        .entity("official", token.getUserId())
                        .summary("Signed out")
                        .record();
            }
        });
    }

    /**
     * Records a refused sign-in without the email that was typed, which may belong to someone who is not an official
     * (#264). An attempt against a real account is tied to it by the official id. An unknown email is kept only as a
     * short hash, enough to see the same address tried again, not to read it back.
     */
    private Outcome refuseLogin(String emailTried, Long officialId, Refusal refusal) {
        String who = officialId != null ? "known account" : "unknown email " + shortHash(emailTried);
        audit.entry(Actor.anonymous(who), "LOGIN_FAILED")
                .entity("official", officialId)
                .summary("Sign-in refused: " + refusal.reason)
                .recordStandalone();
        return new Outcome.Refused(refusal);
    }

    private static String shortHash(String email) {
        String normalised = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return sha256Hex(normalised).substring(0, 8);
    }

    /** Records a refused refresh: someone presented a cookie that did not work. */
    private Outcome refuseRefresh(Long officialId, Refusal refusal) {
        audit.entry(Actor.anonymous("refresh cookie"), "REFRESH_REFUSED")
                .entity("official", officialId)
                .summary("Refresh refused: " + refusal.reason)
                .recordStandalone();
        return new Outcome.Refused(refusal);
    }

    /** A refresh token ready to store, with the raw value the browser will hold (only its hash is stored). */
    private record NewRefreshToken(String rawValue, RefreshToken entity) {}

    private NewRefreshToken newRefreshToken(User user, String familyId) {
        String rawToken = jwtTokenService.generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256Hex(rawToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtTokenService.getRefreshTokenTtlMs()));
        refreshToken.setRevoked(false);
        refreshToken.setFamilyId(familyId);
        return new NewRefreshToken(rawToken, refreshToken);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
