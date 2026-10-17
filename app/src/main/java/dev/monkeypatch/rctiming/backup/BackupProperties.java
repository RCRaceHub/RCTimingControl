package dev.monkeypatch.rctiming.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * {@code rctiming.backup.*}: where database backups go and how many are kept (#22).
 *
 * <p>When the nightly backup runs ({@code rctiming.backup.nightly-cron}) is read by {@code BackupScheduler},
 * the only place that uses it.
 *
 * @param directory   folder for backups, which may be a USB stick or network share; defaults to
 *                    {@code backups} inside the data directory
 * @param keep        how many backups to keep, at least 1; the oldest beyond this are deleted
 */
@ConfigurationProperties(prefix = "rctiming.backup")
public record BackupProperties(
        Path directory,
        @DefaultValue("14") int keep) {

    /** Refuses a {@code keep} below 1, which would delete every backup including the one just taken. */
    public BackupProperties {
        if (keep < 1) {
            throw new IllegalArgumentException("rctiming.backup.keep must be at least 1, not " + keep);
        }
    }
}
