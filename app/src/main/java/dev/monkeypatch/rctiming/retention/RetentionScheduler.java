package dev.monkeypatch.rctiming.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the retention clean-up (#262) once a night. */
@Component
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retention;

    public RetentionScheduler(RetentionService retention) {
        this.retention = retention;
    }

    /** 03:00 server time, an hour after the nightly backup, unless {@code rctiming.retention.cron} says otherwise. */
    @Scheduled(cron = "${rctiming.retention.cron:0 0 3 * * *}")
    public void nightly() {
        try {
            RetentionService.Result result = retention.purge();
            log.info("Retention clean-up deleted {} tokens, {} refused sign-ins, {} audit rows",
                    result.tokens(), result.refusedSignIns(), result.auditRows());
        } catch (RuntimeException e) {
            // A failed clean-up must not take anything else down; it runs again tomorrow
            log.error("Retention clean-up failed", e);
        }
    }
}
