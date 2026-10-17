package dev.monkeypatch.rctiming.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the retention clean-up (#262) once a night, and once when the app starts, because a laptop at the track may be
 * off at 03:00 and a missed run is not caught up.
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retention;
    private final boolean runOnStartup;

    public RetentionScheduler(RetentionService retention,
                              @Value("${rctiming.retention.run-on-startup:true}") boolean runOnStartup) {
        this.retention = retention;
        this.runOnStartup = runOnStartup;
    }

    /** Runs the clean-up once when the app is ready, unless {@code rctiming.retention.run-on-startup} is false. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            nightly();
        }
    }

    /**
     * 03:00 server time, an hour after the nightly backup, unless {@code rctiming.retention.cron} says otherwise;
     * {@code -} turns the nightly run off. Runs on a scheduler thread. A failure is logged and never thrown.
     */
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
