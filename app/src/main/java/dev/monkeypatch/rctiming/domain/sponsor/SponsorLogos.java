package dev.monkeypatch.rctiming.domain.sponsor;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * The checks shared by every kind of sponsor (an event's, an overlay theme's): the name rules and the upload rules
 * for a logo. Logos are PNG or JPEG up to 2 MB (no SVG: the files are served from the app's own origin, where a script
 * in one would run; no WebP, which the JDK cannot decode) and are normalised by {@link SponsorLogoProcessor}. Pure and
 * stateless; called by the sponsor services on the request thread, and throws only {@link IllegalArgumentException},
 * which the API answers as 400.
 */
final class SponsorLogos {

    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg");
    private static final long LOGO_MAX_BYTES = 2 * 1024 * 1024L;
    private static final int NAME_MAX = 255;

    private SponsorLogos() {
    }

    /** The trimmed name. @throws IllegalArgumentException (400) if it is blank or longer than 255 characters */
    static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("A sponsor needs a name of up to " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    /**
     * Checks the upload and turns it into the PNG that is stored. Decoding and scaling can take seconds, so callers
     * run this before loading the sponsor, to keep the write connection the timing thread shares as short as possible.
     *
     * @throws IllegalArgumentException (400) if the file is missing, not an allowed image type, over 2 MB,
     *         unreadable, too large to decode or too small
     */
    static byte[] normalise(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!LOGO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + file.getContentType()
                    + ". Allowed: " + LOGO_TYPES);
        }
        if (file.getSize() > LOGO_MAX_BYTES) {
            throw new IllegalArgumentException("File too large: max " + LOGO_MAX_BYTES + " bytes");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
        return SponsorLogoProcessor.normalise(bytes);
    }
}
