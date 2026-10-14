package dev.monkeypatch.rctiming.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.retention.*}: how long personal data is kept before the nightly clean-up deletes it (#262).
 *
 * @param failedSignInDays how long refused sign-ins and refused refreshes stay in the audit log, at least 1 and at most {@link #MAX_DAYS}
 * @param auditDays        how long every other audit row stays; 0 keeps them for ever, otherwise at least {@code failedSignInDays}
 *
 * <p>When the clean-up runs is read by {@code RetentionScheduler}, the only place that uses it.
 */
@ConfigurationProperties(prefix = "rctiming.retention")
public record RetentionProperties(
        @DefaultValue("90") int failedSignInDays,
        @DefaultValue("730") int auditDays) {

    static final int MAX_DAYS = 36500;

    public RetentionProperties {
        if (failedSignInDays < 1 || failedSignInDays > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "rctiming.retention.failed-sign-in-days must be 1 to " + MAX_DAYS + ", not " + failedSignInDays);
        }
        if (auditDays != 0 && (auditDays < failedSignInDays || auditDays > MAX_DAYS)) {
            throw new IllegalArgumentException("rctiming.retention.audit-days must be 0, or " + failedSignInDays
                    + " to " + MAX_DAYS + " (not less than failed-sign-in-days), not " + auditDays);
        }
    }
}
