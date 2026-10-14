package dev.monkeypatch.rctiming.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.retention.*}: how long personal data is kept before the nightly clean-up deletes it (#262).
 *
 * @param failedSignInDays how long refused sign-ins and refused refreshes stay in the audit log, at least 1
 * @param auditDays        how long every other audit row stays; 0 keeps them for ever
 *
 * <p>When the clean-up runs is read by {@code RetentionScheduler}, the only place that uses it.
 */
@ConfigurationProperties(prefix = "rctiming.retention")
public record RetentionProperties(
        @DefaultValue("90") int failedSignInDays,
        @DefaultValue("730") int auditDays) {

    public RetentionProperties {
        if (failedSignInDays < 1) {
            throw new IllegalArgumentException(
                    "rctiming.retention.failed-sign-in-days must be at least 1, not " + failedSignInDays);
        }
        if (auditDays < 0) {
            throw new IllegalArgumentException("rctiming.retention.audit-days must be 0 or more, not " + auditDays);
        }
    }
}
