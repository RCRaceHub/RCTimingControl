package dev.monkeypatch.rctiming.api.audio;

import dev.monkeypatch.rctiming.infrastructure.tts.AudioPreGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Serves pre-generated audio clip URLs for a race to the race control client (AUDIO-10), under
 * {@code /api/v1/race/{raceId}/audio-clips}. Any official may call it ({@code SecurityConfig}'s last rule; there is
 * no {@code @PreAuthorize}).
 * <p>
 * The race control browser calls {@code GET /api/v1/race/{raceId}/audio-clips} during grid
 * preparation to pre-fetch and locally cache all clip URLs before the race starts.
 */
@RestController
@RequestMapping("/api/v1/race/{raceId}/audio-clips")
public class AudioClipController {

    private final AudioPreGenerationService audioPreGenerationService;

    public AudioClipController(AudioPreGenerationService audioPreGenerationService) {
        this.audioPreGenerationService = audioPreGenerationService;
    }

    /**
     * Returns a map of clip key → storage URL for all pre-generated clips for this race.
     * <p>
     * Example response:
     * <pre>{@code
     * {
     *   "countdown-600": "http://localhost:8080/storage/audio/race/1/countdown-600-en_GB-alan-medium.wav",
     *   "countdown-30":  "http://localhost:8080/storage/audio/race/1/countdown-30-en_GB-alan-medium.wav",
     *   "grid-100":       "http://localhost:8080/storage/audio/race/1/grid-100-en_GB-alan-medium.wav",
     *   "finish":        "http://localhost:8080/storage/audio/race/1/finish-en_GB-alan-medium.wav"
     * }
     * }</pre>
     * Returns an empty object {@code {}} if clips have not yet been generated (race not in GRID).
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getClipMap(@PathVariable Long raceId) {
        Map<String, String> clips = audioPreGenerationService.getClipMap(raceId);
        return ResponseEntity.ok(clips);
    }
}
