package dev.monkeypatch.rctiming.audio;

import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsClipService;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsProperties;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TtsClipServiceTest {

    @Mock
    private PiperTtsClient piperClient;

    @Mock
    private ObjectStorageService storageService;

    private TtsClipService service;

    private static final byte[] FAKE_WAV = new byte[]{0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final TtsProperties PROPS =
            new TtsProperties("piper:10200", "en_GB-alan-medium", true, null);

    @BeforeEach
    void setUp() {
        service = new TtsClipService(piperClient, storageService, PROPS);
    }

    @Test
    void generateRaceFinishedClip_storesWithRaceKey() {
        when(piperClient.synthesize("Race finished. Checkered flag.", "en_GB-cori-high")).thenReturn(FAKE_WAV);
        when(storageService.upload(
                eq("audio/race/3/finish-en_GB-cori-high.wav"),
                any()))
                .thenReturn("http://localhost:8080/storage/rctiming/audio/race/3/finish-en_GB-cori-high.wav");

        String url = service.generateRaceFinishedClip(3L, "Race finished. Checkered flag.", "en_GB-cori-high");

        assertNotNull(url);
        verify(storageService).upload(eq("audio/race/3/finish-en_GB-cori-high.wav"), eq(FAKE_WAV));
    }

    @Test
    void generateClip_piperUnavailable_returnsNull() {
        when(piperClient.synthesize(any(), any()))
                .thenThrow(new TtsUnavailableException("Piper is down"));

        String url = service.generateCountdownClip(1L, 60, "Race, 1 minute", null);

        assertNull(url, "Should return null when Piper is unavailable");
        verify(storageService, never()).upload(any(), any());
    }

    @Test
    void generateClip_ttsDisabled_returnsNullWithoutCallingPiper() {
        TtsClipService disabled = new TtsClipService(piperClient, storageService,
                new TtsProperties("piper:10200", "en_GB-alan-medium", false, null));

        assertNull(disabled.generateCountdownClip(1L, 60, "Race, 1 minute", null));
        verify(piperClient, never()).synthesize(any(), any());
        verify(storageService, never()).upload(any(), any());
    }

    @Test
    void generateCountdownClip_storesWithCorrectKey() {
        when(piperClient.synthesize(eq("Race Finals, 5 minutes"), anyString())).thenReturn(FAKE_WAV);
        when(storageService.upload(
                eq("audio/race/10/countdown-300-en_GB-alan-medium.wav"),
                any()))
                .thenReturn("http://localhost:8080/storage/rctiming/audio/race/10/countdown-300-en_GB-alan-medium.wav");

        String url = service.generateCountdownClip(10L, 300, "Race Finals, 5 minutes", null);

        assertNotNull(url);
        verify(storageService).upload(
                eq("audio/race/10/countdown-300-en_GB-alan-medium.wav"),
                eq(FAKE_WAV));
    }

    @Test
    void generateGridCallClip_storesWithEntryKey() {
        when(piperClient.synthesize(eq("Alan Smith."), anyString())).thenReturn(FAKE_WAV);
        when(storageService.upload(
                eq("audio/race/5/grid-42-en_GB-alan-medium.wav"),
                any()))
                .thenReturn("http://localhost:8080/storage/");

        service.generateGridCallClip(5L, 42L, "Alan Smith.", null);

        verify(storageService).upload(
                eq("audio/race/5/grid-42-en_GB-alan-medium.wav"),
                eq(FAKE_WAV));
    }
}
