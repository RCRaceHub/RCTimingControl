package dev.monkeypatch.rctiming.infrastructure.settings;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code application.properties} file in the data folder (#510), where an install keeps the settings the app reads
 * only when it starts (the port, HTTPS, the mDNS name and so on). The Club page uses it to switch HTTPS on and off, so
 * the file is the one place such a choice is written; the setting takes effect at the next restart.
 *
 * <p>Only the one key asked for is changed. Every other line, including comments and blank lines, is kept as it is, and
 * the file is written to a temporary file and moved into place, so a crash cannot leave it half written, and it
 * keeps its permissions. It is read and written as ISO-8859-1, as Spring Boot reads {@code .properties} files. Called from
 * request threads; {@code synchronized}, because two admins saving at once would otherwise lose one change. A value
 * given by an environment variable (as the Docker image does) still wins over the file.
 */
@Component
public class SettingsFile {

    /** The charset Spring Boot reads {@code .properties} files in, so a line this class does not touch is kept byte for byte. */
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private final Path file;

    @Autowired
    public SettingsFile(DatabaseProperties database) {
        this(database.effectiveDataDirectory().resolve("application.properties"));
    }

    SettingsFile(Path file) {
        this.file = file;
    }

    /** The value the file gives {@code key}, or empty when the file or the key is missing. */
    public synchronized Optional<String> get(String key) {
        String found = null;
        for (String line : lines()) {
            if (isKey(line, key)) {
                found = valueOf(line);   // as Spring does, the last of a repeated key wins
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * Sets {@code key} to {@code value}, replacing its line or adding one at the end, and creating the file if there is
     * none.
     *
     * @throws IllegalStateException if the file cannot be written
     */
    public synchronized void set(String key, String value) {
        if (hasLineBreak(key) || hasLineBreak(value)) {
            throw new IllegalArgumentException("A setting cannot contain a line break");
        }
        List<String> lines = new ArrayList<>(lines());
        String replacement = key + "=" + value;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (isKey(lines.get(i), key)) {
                if (found) {
                    lines.remove(i--);
                } else {
                    lines.set(i, replacement);
                    found = true;
                }
            }
        }
        if (!found) {
            lines.add(replacement);
        }
        write(lines);
    }

    private List<String> lines() {
        try {
            return Files.exists(file) ? Files.readAllLines(file, CHARSET) : List.of();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    private void write(List<String> lines) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "application", ".tmp");
            try {
                Files.write(temporary, lines, CHARSET);
                copyPermissions(file, temporary);
                move(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file, e);
        }
    }

    private static boolean hasLineBreak(String text) {
        return text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\0') >= 0;
    }

    /** Gives the new file the old file's permissions, so a file an admin made readable stays readable. */
    private static void copyPermissions(Path from, Path to) {
        try {
            PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class);
            if (source != null && Files.exists(from)) {
                Files.setPosixFilePermissions(to, source.readAttributes().permissions());
            }
        } catch (IOException | UnsupportedOperationException e) {
            // Windows has no POSIX permissions; the folder's own access rules apply there
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isKey(String line, String key) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return false;
        }
        int end = 0;
        while (end < trimmed.length() && "=: \t".indexOf(trimmed.charAt(end)) < 0) {
            end++;
        }
        return trimmed.substring(0, end).equals(key);
    }

    private static String valueOf(String line) {
        String trimmed = line.stripLeading();
        int i = 0;
        while (i < trimmed.length() && "=: \t".indexOf(trimmed.charAt(i)) < 0) {
            i++;
        }
        while (i < trimmed.length() && " \t".indexOf(trimmed.charAt(i)) >= 0) {
            i++;
        }
        if (i < trimmed.length() && "=:".indexOf(trimmed.charAt(i)) >= 0) {
            i++;
        }
        return trimmed.substring(i).strip();
    }
}
