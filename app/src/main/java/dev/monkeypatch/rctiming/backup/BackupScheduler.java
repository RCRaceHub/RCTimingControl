package dev.monkeypatch.rctiming.backup;

import dev.monkeypatch.rctiming.domain.event.EventCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Takes the automatic backups (#22): one when a race day closes, and one every night. */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * After the event is saved as completed, so the backup includes the day's final results. Runs after the
     * closing transaction commits, on an async executor thread, so closing the day never waits for the copy.
     */
    @Async
    @TransactionalEventListener
    public void onDayClosed(EventCompleted completed) {
        run("day-close");
    }

    /**
     * 02:00 server time unless {@code rctiming.backup.nightly-cron} says otherwise; {@code -} turns it off. Runs on
     * a scheduler thread.
     */
    @Scheduled(cron = "${rctiming.backup.nightly-cron:0 0 2 * * *}")
    public void nightly() {
        run("nightly");
    }

    /** Takes the backup and logs a failure rather than throwing, as neither caller has anyone to tell. */
    private void run(String reason) {
        try {
            backupService.backup(reason);
        } catch (RuntimeException e) {
            // A failed automatic backup must not take anything else down; the admin page shows the gap
            log.error("Automatic {} backup failed", reason, e);
        }
    }
}
