package dev.monkeypatch.rctiming.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * The key officials' sign-in tokens are signed with, when {@code JWT_SECRET} is not set (#23).
 *
 * <p>An installed copy is reachable from every device on the venue network, so it must not sign
 * tokens with a key anyone can read in the source. The first start writes a random key to
 * {@code jwt-secret} in the data folder and later starts reuse it, so sign-ins survive restarts.
 * Anyone who can read the key can sign themselves an ADMIN token, so every start makes sure only
 * the file's owner can read it, and refuses to start if it can't.
 *
 * <p>Despite the name, the key-file logic is not specific to JWTs: {@link TokenCipher} reuses it under another file
 * name for its own key.
 */
final class JwtSecretFile {

    static final String FILE_NAME = "jwt-secret";

    private JwtSecretFile() {
    }

    /** Returns the base64 key stored in {@code dataDirectory}, creating it on first use. */
    static String loadOrCreate(Path dataDirectory) {
        return loadOrCreate(dataDirectory, FILE_NAME, "sign-in key", "set JWT_SECRET instead of using a key file");
    }

    /**
     * Returns the random base64 key in {@code fileName} in {@code dataDirectory}, creating it on first use, and
     * makes sure only the file's owner can read it. Also used for the key entry feed tokens are encrypted with
     * (#42).
     *
     * @param purpose     what the key is for, for error messages
     * @param alternative what to do instead when the file system can't limit who reads the file
     */
    static String loadOrCreate(Path dataDirectory, String fileName, String purpose, String alternative) {
        Path file = dataDirectory.resolve(fileName);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataDirectory);
                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                try {
                    Files.writeString(file, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException raced) {
                    // Another process wrote it first; use theirs
                }
            }
            restrictToOwner(file, alternative);
            String secret = Files.readString(file, StandardCharsets.US_ASCII).trim();
            if (secret.isEmpty()) {
                throw new IllegalStateException(file + " is empty; delete it to generate a new key");
            }
            return secret;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or create the " + purpose + " at " + file, e);
        }
    }

    /**
     * Lets only the file's owner read it: the service account for the installed app, or the person
     * who started it by hand. On Windows the ProgramData folder lets every local user read what is
     * in it, so the inherited access list is replaced with one for the owner alone.
     */
    private static void restrictToOwner(Path file, String alternative) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build()));
            return;
        }
        throw new IOException("This file system can't limit who reads " + file + "; " + alternative);
    }
}
