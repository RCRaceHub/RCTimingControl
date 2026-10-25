package dev.monkeypatch.rctiming.infrastructure.storage;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores uploads on local disk under {@link StorageFolder}, served back over HTTP by
 * {@link dev.monkeypatch.rctiming.config.StaticStorageConfig}'s resource handler. One app on the
 * venue laptop needs no separate object-storage server.
 */
@Service
public class FilesystemObjectStorageService implements ObjectStorageService {

    private final Path rootDir;
    private final String publicBaseUrl;

    public FilesystemObjectStorageService(@Value("${storage.local-path:}") String localPath,
                                           @Value("${storage.public-base-url}") String publicBaseUrl,
                                           DatabaseProperties database) {
        this.rootDir = StorageFolder.resolve(localPath, database);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage directory: " + rootDir, e);
        }
    }

    /**
     * Writes the bytes under the storage folder, replacing any file at that key, and returns
     * {@code storage.public-base-url} plus the key. Blocks on disk I/O on the calling thread.
     *
     * @throws IllegalArgumentException if the key would leave the storage folder
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    @Override
    public String upload(String key, byte[] content) {
        Path target = rootDir.resolve(key).normalize();
        // Keys are built server-side (LogoUploadService, TtsClipService) from fixed prefixes
        // plus numeric IDs, never from raw user input — this guard is defense in depth against
        // a future caller passing an unsanitized key, not a response to an observed attack path.
        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write object: " + key, e);
        }
        return publicBaseUrl + "/" + key;
    }

    /**
     * Deletes the file at the URL's key. Blocks on disk I/O on the calling thread. A URL outside {@code
     * storage.public-base-url}, a key that would leave the storage folder or a file that is already gone does nothing.
     *
     * @throws java.io.UncheckedIOException if the file exists but cannot be deleted
     */
    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(publicBaseUrl + "/")) {
            return;
        }
        String key = url.substring(publicBaseUrl.length() + 1);
        Path target = rootDir.resolve(key).normalize();
        if (key.isBlank() || target.equals(rootDir) || !target.startsWith(rootDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete object: " + url, e);
        }
    }
}
