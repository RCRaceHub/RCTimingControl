package dev.monkeypatch.rctiming.infrastructure.storage;

/**
 * Where uploaded files (club logos, announcer clips) are kept. The only implementation writes to local disk
 * ({@link FilesystemObjectStorageService}); the interface keeps callers from knowing that.
 */
public interface ObjectStorageService {
    /**
     * Uploads content under the given key and returns a retrievable URL.
     *
     * @param key     object key, e.g. "club-logos/42.png"
     * @param content raw bytes
     * @return retrievable URL (public or signed)
     */
    String upload(String key, byte[] content);

    /**
     * Removes the file an earlier {@link #upload} returned this URL for, so personal data such as a headshot does not
     * outlive the record that pointed at it. A URL that is not one of this store's, or a file already gone, is
     * ignored.
     *
     * @param url the URL {@link #upload} returned
     */
    default void delete(String url) {
    }
}
