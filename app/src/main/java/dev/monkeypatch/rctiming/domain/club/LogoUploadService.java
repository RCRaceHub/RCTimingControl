package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Stores the club's logo image in object storage and points the club profile at it. It accepts PNG, JPEG or WebP
 * up to 2 MB (never SVG, which can carry a script and would run on the app's own origin) and records {@code CLUB_LOGO_CHANGED}. The previous file stays in storage.
 */
@Service
@Transactional
public class LogoUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");

    private static final long MAX_BYTES = 2 * 1024 * 1024L; // 2 MB

    private final ObjectStorageService storage;
    private final ClubProfileRepository clubProfileRepository;
    private final AuditService audit;

    public LogoUploadService(ObjectStorageService storage,
                              ClubProfileRepository clubProfileRepository,
                              AuditService audit) {
        this.storage = storage;
        this.clubProfileRepository = clubProfileRepository;
        this.audit = audit;
    }

    /**
     * Saves the image and sets it as the profile's logo, in the caller's write transaction.
     *
     * @return the URL the logo is served from
     * @throws IllegalArgumentException if the file is missing, not an allowed image type, over 2 MB or unreadable
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if there is no such club profile
     */
    public String uploadLogo(Actor actor, Long clubProfileId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large: max " + MAX_BYTES + " bytes");
        }
        ClubProfile profile = clubProfileRepository.getOrThrow(clubProfileId);

        String extension = switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "bin";
        };
        String key = "club-logos/" + clubProfileId + "-" + System.currentTimeMillis() + "." + extension;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }

        String url = storage.upload(key, bytes);
        String before = profile.getLogoUrl();
        profile.setLogoUrl(url);
        clubProfileRepository.save(profile);
        audit.entry(actor, "CLUB_LOGO_CHANGED").entity("club_profile", clubProfileId)
                .summary("Replaced the club logo")
                .before(before).after(url).record();
        return url;
    }
}
