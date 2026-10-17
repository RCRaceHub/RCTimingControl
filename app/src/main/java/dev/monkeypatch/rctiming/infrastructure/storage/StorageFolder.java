package dev.monkeypatch.rctiming.infrastructure.storage;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;

import java.nio.file.Path;

/** Where uploads live: {@code storage.local-path} when set, else an {@code uploads} folder beside the database. */
public final class StorageFolder {

    private StorageFolder() {
    }

    /** The absolute, normalised storage folder; shared by the writer and the {@code /storage/**} handler so they agree. */
    public static Path resolve(String localPath, DatabaseProperties database) {
        Path folder = localPath == null || localPath.isBlank()
                ? database.effectiveDataDirectory().resolve("uploads")
                : Path.of(localPath);
        return folder.toAbsolutePath().normalize();
    }
}
