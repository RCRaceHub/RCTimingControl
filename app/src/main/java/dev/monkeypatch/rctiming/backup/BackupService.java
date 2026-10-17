package dev.monkeypatch.rctiming.backup;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies the live database into the backup folder and keeps the newest few (#22). The copy is
 * made by the database vendor, so it is consistent even while a race is being timed.
 *
 * <p>Backups are named {@code rctiming-yyyyMMdd-HHmmss-reason.db} (UTC), written under a {@code .partial} name,
 * checked, and only then moved into place, so the folder never holds a half-written backup under a real name.
 * Callers are the admin API on request threads and {@link BackupScheduler}.
 */
@Service
@EnableConfigurationProperties(BackupProperties.class)
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Pattern NAME = Pattern.compile("rctiming-(\\d{8}-\\d{6})(?:-\\d+)?-([a-z-]+)\\.db");

    private final DatabaseProperties database;
    private final BackupProperties properties;

    public BackupService(DatabaseProperties database, BackupProperties properties) {
        this.database = database;
        this.properties = properties;
    }

    /** The backup folder; {@code backups} inside the data directory unless configured. */
    public Path directory() {
        return properties.directory() != null
                ? properties.directory()
                : database.effectiveDataDirectory().resolve("backups");
    }

    /**
     * Takes a backup now and deletes the oldest beyond the number kept.
     *
     * <p>Synchronized, so two backups never write at once. Blocks the caller for the length of the copy.
     *
     * @param reason a short lower-case label stored in the file name, such as {@code manual}
     * @throws BackupFailedException if the folder is missing or the copy fails or does not pass the vendor's check;
     *                               a failure to delete old backups is only logged
     */
    public synchronized BackupFile backup(String reason) {
        Path partial = null;
        try {
            Path folder = backupFolder();
            Path target = uniqueTarget(folder, Instant.now(), reason);
            partial = target.resolveSibling(target.getFileName() + ".partial");
            Files.deleteIfExists(partial);
            database.vendor().backup(database.effectiveDataDirectory(), partial);
            database.vendor().checkBackup(partial);
            Files.move(partial, target);
            log.info("Database backed up to {}", target);
            tidyOldBackups();
            return describe(target);
        } catch (IOException | SQLException e) {
            deleteQuietly(partial, e);
            throw new BackupFailedException("Backup to " + directory() + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Backups in the folder, newest first, by file time. Files that do not match the backup naming are ignored.
     *
     * @throws BackupFailedException if a configured folder is missing
     */
    public List<BackupFile> list() {
        Path folder = directory();
        if (!Files.isDirectory(folder)) {
            if (properties.directory() != null) {
                throw missingFolder(folder);
            }
            // The default folder appears with the first backup
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(f -> NAME.matcher(f.getFileName().toString()).matches())
                    .map(this::describe)
                    .sorted(Comparator.comparing(BackupFile::createdAt).thenComparing(BackupFile::name).reversed())
                    .toList();
        } catch (IOException e) {
            throw new BackupFailedException("Can't list backups in " + folder + ": " + e.getMessage(), e);
        }
    }

    /**
     * The folder to write to. The default folder is created when needed. A configured one, often a
     * USB stick or network share, must already be there: creating it would put backups on the
     * laptop's own disk when the stick is unplugged, and report success.
     */
    private Path backupFolder() throws IOException {
        Path folder = directory();
        if (properties.directory() == null) {
            return Files.createDirectories(folder);
        }
        if (!Files.isDirectory(folder)) {
            throw missingFolder(folder);
        }
        return folder;
    }

    private static BackupFailedException missingFolder(Path folder) {
        return new BackupFailedException("The backup folder " + folder
                + " is not there. Check the USB stick or network share is connected.", null);
    }

    private static void deleteQuietly(Path partial, Exception cause) {
        if (partial == null) {
            return;
        }
        try {
            Files.deleteIfExists(partial);
        } catch (IOException e) {
            cause.addSuppressed(e);
        }
    }

    /**
     * Deletes the oldest backups beyond the number kept. The new backup is already in place, so a failure
     * here is logged and not thrown: it must not make a good backup report as failed.
     */
    private void tidyOldBackups() {
        try {
            prune();
        } catch (IOException | BackupFailedException e) {
            log.warn("Backup taken, but old backups in {} could not be deleted: {}", directory(), e.getMessage());
        }
    }

    private void prune() throws IOException {
        List<BackupFile> backups = list();
        for (BackupFile old : backups.subList(Math.min(properties.keep(), backups.size()), backups.size())) {
            Files.deleteIfExists(directory().resolve(old.name()));
            log.info("Deleted old backup {}", old.name());
        }
    }

    /** The file name for a backup at {@code now}, with a number added if one was already taken this second. */
    private static Path uniqueTarget(Path folder, Instant now, String reason) {
        String stamp = STAMP.format(now);
        Path target = folder.resolve("rctiming-" + stamp + "-" + reason + ".db");
        for (int n = 2; Files.exists(target); n++) {
            target = folder.resolve("rctiming-" + stamp + "-" + n + "-" + reason + ".db");
        }
        return target;
    }

    private BackupFile describe(Path file) {
        String name = file.getFileName().toString();
        Matcher matcher = NAME.matcher(name);
        try {
            Instant created = Files.getLastModifiedTime(file).toInstant();
            return new BackupFile(name, Files.size(file), created, matcher.matches() ? matcher.group(2) : "");
        } catch (IOException e) {
            throw new BackupFailedException("Can't read backup " + file + ": " + e.getMessage(), e);
        }
    }
}
