package dev.monkeypatch.rctiming.infrastructure.tts;

import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates TTS announcement clips, storing them via ObjectStorageService.
 * <p>
 * Key conventions:
 * <ul>
 *   <li>{@code audio/race/{raceId}/countdown-{seconds}-{voiceId}.wav} — countdown clip</li>
 *   <li>{@code audio/race/{raceId}/grid-{entryId}-{voiceId}.wav} — grid call (stagger) clip for one entry</li>
 *   <li>{@code audio/race/{raceId}/finish-{voiceId}.wav} — race-finished clip</li>
 * </ul>
 * When Piper is unavailable, methods log a warning and return {@code null} (graceful degradation).
 */
@Service
public class TtsClipService {

    private static final Logger log = LoggerFactory.getLogger(TtsClipService.class);

    private final PiperTtsClient piperClient;
    private final ObjectStorageService storageService;
    private final TtsProperties properties;

    public TtsClipService(PiperTtsClient piperClient,
                          ObjectStorageService storageService,
                          TtsProperties properties) {
        this.piperClient = piperClient;
        this.storageService = storageService;
        this.properties = properties;
    }

    /**
     * Generate and store a countdown interval clip.
     *
     * @param raceId   database ID of the race
     * @param seconds  seconds remaining to announce (e.g. 600, 300, 120, 60, 30)
     * @param text     full announcement text (e.g. "5 minutes remaining.")
     * @param voiceId  Piper voice model name, or null to use default
     * @return storage public URL, or null if Piper was unavailable
     */
    public String generateCountdownClip(Long raceId, int seconds, String text, String voiceId) {
        String effectiveVoice = resolve(voiceId);
        String key = String.format("audio/race/%d/countdown-%d-%s.wav", raceId, seconds, effectiveVoice);
        return synthesizeAndUpload(key, text, effectiveVoice,
                "race {} countdown {}s clip", raceId, seconds);
    }

    /**
     * Generate and store the grid-call clip for one entry (the stagger call).
     *
     * @param raceId  database ID of the race
     * @param entryId database ID of the entry being called
     * @param text    announcement text (e.g. "Alan Smith.")
     * @param voiceId Piper voice model name, or null to use default
     * @return storage public URL, or null if Piper was unavailable
     */
    public String generateGridCallClip(Long raceId, Long entryId, String text, String voiceId) {
        String effectiveVoice = resolve(voiceId);
        String key = String.format("audio/race/%d/grid-%d-%s.wav", raceId, entryId, effectiveVoice);
        return synthesizeAndUpload(key, text, effectiveVoice,
                "race {} grid call entry {} clip", raceId, entryId);
    }

    /**
     * Generate and store the race-finished clip, played once when the race ends.
     *
     * @param raceId  database ID of the race
     * @param text    announcement text (e.g. "Race finished. Checkered flag.")
     * @param voiceId Piper voice model name, or null to use default
     * @return storage public URL, or null if Piper was unavailable
     */
    public String generateRaceFinishedClip(Long raceId, String text, String voiceId) {
        String effectiveVoice = resolve(voiceId);
        String key = String.format("audio/race/%d/finish-%s.wav", raceId, effectiveVoice);
        return synthesizeAndUpload(key, text, effectiveVoice,
                "race {} finished clip", raceId);
    }

    private String resolve(String voiceId) {
        return (voiceId != null && !voiceId.isBlank()) ? voiceId : properties.defaultVoice();
    }

    private String synthesizeAndUpload(String key, String text, String voice,
                                        String logPattern, Object... logArgs) {
        try {
            byte[] wavData = piperClient.synthesize(text, voice);
            return storageService.upload(key, wavData);
        } catch (TtsUnavailableException e) {
            Object[] fullArgs = new Object[logArgs.length + 1];
            System.arraycopy(logArgs, 0, fullArgs, 0, logArgs.length);
            fullArgs[logArgs.length] = e.getMessage();
            log.warn("TTS unavailable generating " + logPattern + ": {}", fullArgs);
            return null;
        }
    }
}
