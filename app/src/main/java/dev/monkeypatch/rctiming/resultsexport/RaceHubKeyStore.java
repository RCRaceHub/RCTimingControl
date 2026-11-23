package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * The club's key for RaceHub when an official has pasted a new one into the admin panel, so a key can be rotated
 * without editing {@code application.properties} or restarting. It sits in {@code racehub-key} in the data folder,
 * encrypted with {@link TokenCipher} and readable only by the file's owner (where the file system allows it), and
 * is never in the database, so a backup or a copy of it does not carry the key.
 *
 * <p>A saved key wins over {@code rctiming.racehub.token}; clearing it brings the settings file's key back. Read by
 * {@link RaceHubResultsEndpoint#token()} on every call to RaceHub, from any thread, so a change applies to the next
 * request. A file that cannot be decrypted (another laptop, a replaced cipher key) is logged and ignored, so the
 * settings file's key still works.
 */
@Component
public class RaceHubKeyStore {

    static final String FILE_NAME = "racehub-key";
    private static final Logger log = LoggerFactory.getLogger(RaceHubKeyStore.class);

    private final Path file;
    private final TokenCipher cipher;
    private volatile Optional<String> cached;

    @Autowired
    public RaceHubKeyStore(DatabaseProperties database, TokenCipher cipher) {
        this(database.effectiveDataDirectory().resolve(FILE_NAME), cipher);
    }

    RaceHubKeyStore(Path file, TokenCipher cipher) {
        this.file = file;
        this.cipher = cipher;
    }

    /** A store that never holds a key, for a caller that only has the settings file's. */
    static RaceHubKeyStore none() {
        return new RaceHubKeyStore((Path) null, null);
    }

    /** The saved key, or empty when none is saved or it cannot be read. Never log or return it. */
    public Optional<String> saved() {
        Optional<String> current = cached;
        if (current == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = load();
                }
                current = cached;
            }
        }
        return current;
    }

    /** Saves {@code key} (already trimmed and checked), replacing any earlier one. Takes effect at once. */
    public synchronized void save(String key) {
        try {
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.createDirectories(file.getParent());
            Files.writeString(temp, cipher.encrypt(key), StandardCharsets.US_ASCII);
            try {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                // Not a POSIX file system (Windows): the data folder's own access rules apply
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the RaceHub key", e);
        }
        cached = Optional.of(key);
    }

    /** Forgets the saved key, so the settings file's applies again. */
    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove the saved RaceHub key", e);
        }
        cached = Optional.empty();
    }

    private Optional<String> load() {
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(cipher.decrypt(Files.readString(file, StandardCharsets.US_ASCII).trim()));
        } catch (IOException | TokenCipher.UnreadableTokenException e) {
            log.warn("The saved RaceHub key can't be read, so the settings file's key is used: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
