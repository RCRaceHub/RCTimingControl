package dev.monkeypatch.rctiming.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.retention.*}: how long personal data is kept before the nightly clean-up deletes it (#262).
 *
 * <p>When the clean-up runs ({@code rctiming.retention.cron}) is read by {@code RetentionScheduler}, the only place
 * that uses it.
 *
 * @param failedSignInDays how long refused sign-ins and refused refreshes stay in the audit log, at least 1 and at most {@link #MAX_DAYS}
 * @param auditDays        how long every other audit row stays; 0 keeps them for ever, otherwise at least {@code failedSignInDays}
 * @param runOnStartup     whether the clean-up also runs once when the app starts, because a laptop at the track may be
 *                         off at the nightly time and a missed run is not caught up
 */
@ConfigurationProperties(prefix = "rctiming.retention")
public record RetentionProperties(
        @DefaultValue("90") int failedSignInDays,
        @DefaultValue("730") int auditDays,
        @DefaultValue("true") boolean runOnStartup) {

    /** About a hundred years: a ceiling that stops a typo overflowing the date arithmetic, not a real policy. */
    static final int MAX_DAYS = 36500;

    /**
     * Refuses values that would stop the app starting with a clear message. An audit limit shorter than the
     * refused-sign-in limit is refused, since it would delete the other rows sooner than the ones meant to go first.
     */
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
