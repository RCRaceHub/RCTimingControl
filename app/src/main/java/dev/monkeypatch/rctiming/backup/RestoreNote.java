package dev.monkeypatch.rctiming.backup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * What the {@code restore} command leaves beside the database for the app's next start (#139). The restore
 * replaces the database file, so a row written to it then would be overwritten by the backup's own contents;
 * the app records the restore in the audit log of the database it has just opened, and removes the note.
 */
record RestoreNote(String backup, String osUser, Instant at) {

    static final String FILE_NAME = "restore-pending.properties";

    /** Leaves the note beside the database, naming the backup, the OS user running the command and the time. */
    static void write(Path dataDirectory, Path backup) throws IOException {
        Properties p = new Properties();
        p.setProperty("backup", backup.toString());
        p.setProperty("osUser", System.getProperty("user.name", "unknown"));
        p.setProperty("at", Instant.now().toString());
        try (Writer out = Files.newBufferedWriter(dataDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8)) {
            p.store(out, "Read once by the app on its next start, then removed");
        }
    }

    /** The pending note, or empty if there is none. Missing fields are filled with placeholders, not refused. */
    static Optional<RestoreNote> read(Path dataDirectory) throws IOException {
        Path file = dataDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        }
        String at = p.getProperty("at");
        return Optional.of(new RestoreNote(p.getProperty("backup", "an unknown file"),
                p.getProperty("osUser", "unknown"), at == null ? Instant.now() : Instant.parse(at)));
    }

    /** Removes the note once it has been recorded. */
    static void remove(Path dataDirectory) throws IOException {
        Files.deleteIfExists(dataDirectory.resolve(FILE_NAME));
    }
}
