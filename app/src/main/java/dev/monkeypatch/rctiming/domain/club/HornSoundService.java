package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * The club's own start and finish horn, tick and lap beep sounds (#278, #281, #299). A club uploads a short
 * sound file to replace a built-in sound, or removes it to go back to the built-in one. The browser tells us how long the sound plays,
 * so race control can wait that long before the spoken line.
 */
@Service
public class HornSoundService {

    public enum Horn {
        START, FINISH, TICK, LAP_BEEP;

        /** The name in a URL and a file name: {@code start}, {@code finish}, {@code tick} or {@code lap-beep}. */
        public String slug() {
            return name().toLowerCase().replace('_', '-');
        }

        /** The sound named in a URL. */
        public static Horn of(String name) {
            for (Horn horn : values()) {
                if (horn.slug().equalsIgnoreCase(name)) {
                    return horn;
                }
            }
            throw new IllegalArgumentException("Unknown horn: " + name + ". Use start, finish, tick or lap-beep");
        }
    }

    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave", "audio/ogg", "application/ogg");
    static final long MAX_BYTES = 1024 * 1024L;
    static final int MIN_MS = 100;
    static final int MAX_MS = 10_000;

    private final ObjectStorageService storage;
    private final ClubProfileService clubProfileService;

    public HornSoundService(ObjectStorageService storage, ClubProfileService clubProfileService) {
        this.storage = storage;
        this.clubProfileService = clubProfileService;
    }

    /** Stores the file as the club's {@code horn} sound and returns the settings as saved. */
    public ClubAudioSettings upload(Actor actor, Horn horn, MultipartFile file, int durationMs) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + file.getContentType() + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large: max " + MAX_BYTES + " bytes");
        }
        if (durationMs < MIN_MS || durationMs > MAX_MS) {
            throw new IllegalArgumentException("durationMs must be from " + MIN_MS + " to " + MAX_MS);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
        String extension = switch (contentType) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/ogg", "application/ogg" -> "ogg";
            default -> "wav";
        };
        String url = storage.upload("horns/" + horn.slug() + "-" + System.currentTimeMillis()
                + "." + extension, bytes);
        return change(actor, horn, url, durationMs);
    }

    /** Goes back to the built-in sound. The old file stays in storage, which has no delete; it is at most 1 MB. */
    public ClubAudioSettings remove(Actor actor, Horn horn) {
        return change(actor, horn, null, null);
    }

    private ClubAudioSettings change(Actor actor, Horn horn, String url, Integer ms) {
        return clubProfileService.changeAudioSettings(actor, profile -> {
            ClubAudioSettings s = profile.getAudioSettings();
            ClubAudioSettings.HornSounds sounds = switch (horn) {
                case START -> s.hornSounds().withStart(url, ms);
                case FINISH -> s.hornSounds().withFinish(url, ms);
                case TICK -> s.hornSounds().withTick(url, ms);
                case LAP_BEEP -> s.hornSounds().withLapBeep(url, ms);
            };
            profile.setAudioSettings(s.withHornSounds(sounds));
        }).getAudioSettings();
    }
}
