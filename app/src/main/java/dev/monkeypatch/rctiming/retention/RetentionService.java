package dev.monkeypatch.rctiming.retention;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditLogRepository;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.auth.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes personal data that has outlived its use (#262): sign-in tokens that can no longer work, refused sign-ins,
 * and, if the club sets a limit, old audit rows.
 *
 * <p>Runs in one write transaction, so a failure leaves everything as it was. When anything went, it writes one audit
 * row of its own with the counts and no names, so the log shows that the clean-up happened.
 */
@Service
public class RetentionService {

    /** The audit actions written by someone who is not signed in; they are the ones a stranger can add to. */
    static final List<String> REFUSED_ACTIONS = List.of("LOGIN_FAILED", "REFRESH_REFUSED");

    /** A revoked token is kept this long, so a sign-out is still there to explain a refused refresh the same day. */
    static final Duration REVOKED_TOKEN_GRACE = Duration.ofDays(1);

    /** What one run deleted. */
    public record Result(int tokens, int refusedSignIns, int auditRows) {
        boolean any() {
            return tokens + refusedSignIns + auditRows > 0;
        }
    }

    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final RetentionProperties properties;
    private final Clock clock;

    @Autowired
    public RetentionService(RefreshTokenRepository refreshTokens, AuditLogRepository auditLog, AuditService audit,
                            RetentionProperties properties) {
        this(refreshTokens, auditLog, audit, properties, Clock.systemUTC());
    }

    RetentionService(RefreshTokenRepository refreshTokens, AuditLogRepository auditLog, AuditService audit,
                     RetentionProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.auditLog = auditLog;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public RetentionProperties policy() {
        return properties;
    }

    @Transactional
    public Result purge() {
        Instant now = clock.instant();
        int tokens = refreshTokens.deleteDead(now, now.minus(REVOKED_TOKEN_GRACE));
        int refused = auditLog.deleteOlderThan(REFUSED_ACTIONS, now.minus(Duration.ofDays(properties.failedSignInDays())));
        int old = properties.auditDays() == 0 ? 0
                : auditLog.deleteOlderThan(now.minus(Duration.ofDays(properties.auditDays())));
        Result result = new Result(tokens, refused, old);
        if (result.any()) {
            audit.entry(Actor.system("retention"), "RETENTION_PURGE")
                    .summary("Deleted " + tokens + " sign-in tokens, " + refused + " refused sign-ins and " + old
                            + " old audit rows")
                    .after(result)
                    .record();
        }
        return result;
    }
}
