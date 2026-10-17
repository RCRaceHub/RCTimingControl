package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts access tokens RCTC keeps for other systems, such as an event's entry feed token (#42), so a copy of
 * the database alone (a backup, say) doesn't give them away.
 *
 * <p>AES-GCM with a random key in {@code entry-feed-key} in the data folder, readable only by the file's owner
 * like the sign-in key. The key is made on first use. A token encrypted on another laptop, or before the key file
 * was replaced, can't be decrypted: {@link #decrypt} throws {@link UnreadableTokenException} and the token has to
 * be entered again.
 */
@Component
public class TokenCipher {

    static final String FILE_NAME = "entry-feed-key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final DatabaseProperties database;
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKey key;

    public TokenCipher(DatabaseProperties database) {
        this.database = database;
    }

    /** The token encrypted, as base64 of the IV followed by the ciphertext. */
    public String encrypt(String token) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the token", e);
        }
    }

    /**
     * The token as {@link #encrypt} wrote it.
     *
     * @throws UnreadableTokenException if it is malformed, was altered, or was encrypted with another key
     */
    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length <= IV_BYTES) {
                throw new UnreadableTokenException();
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new UnreadableTokenException();
        }
    }

    /** The key, read (or made) on first use and then kept; double-checked so concurrent first calls agree. */
    private SecretKey key() {
        SecretKey current = key;
        if (current == null) {
            synchronized (this) {
                if (key == null) {
                    String base64 = JwtSecretFile.loadOrCreate(database.effectiveDataDirectory(), FILE_NAME,
                            "entry feed key", "entry feed tokens can't be kept safely on this computer");
                    key = new SecretKeySpec(Base64.getDecoder().decode(base64), "AES");
                }
                current = key;
            }
        }
        return current;
    }

    /** The token was encrypted with another key, such as on another laptop, so it has to be entered again. */
    public static class UnreadableTokenException extends RuntimeException {
        public UnreadableTokenException() {
            super("The saved token can't be read on this computer. Enter it again.");
        }
    }
}
