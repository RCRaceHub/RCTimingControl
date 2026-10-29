package dev.monkeypatch.rctiming.audio;

import dev.monkeypatch.rctiming.TestJson;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsProperties;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import dev.monkeypatch.rctiming.infrastructure.tts.VoiceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class PiperTtsClientTest {

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int port;
    private final ObjectMapper objectMapper = TestJson.mapper();

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // bind to any free port
        port = serverSocket.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }

    private PiperTtsClient clientFor(boolean enabled) {
        TtsProperties props = new TtsProperties("localhost:" + port, "en_GB-alan-medium", enabled, List.of("en_GB"));
        return new PiperTtsClient(props, objectMapper);
    }

    /** Serve a minimal Wyoming synthesize response in a background thread. */
    private void serveSynthesizeResponse(byte[] pcmData) {
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                // Discard the incoming JSON line
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine();

                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), false);
                OutputStream rawOut = client.getOutputStream();

                // audio-start
                writer.print("{\"type\":\"audio-start\",\"data\":{\"rate\":22050,\"width\":2,\"channels\":1}}\n");
                writer.flush();

                // audio-chunk with binary payload
                String chunkHeader = "{\"type\":\"audio-chunk\",\"data\":{},\"payload_length\":" + pcmData.length + "}\n";
                rawOut.write(chunkHeader.getBytes(StandardCharsets.UTF_8));
                rawOut.write(pcmData);
                rawOut.flush();

                // audio-stop
                rawOut.write("{\"type\":\"audio-stop\"}\n".getBytes(StandardCharsets.UTF_8));
                rawOut.flush();
            } catch (IOException e) {
                // test server closed — expected
            }
        });
    }

    @Test
    void synthesize_validText_returnsWavBytes() throws Exception {
        byte[] fakePcm = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        serveSynthesizeResponse(fakePcm);

        PiperTtsClient client = clientFor(true);
        byte[] wav = client.synthesize("Hello", "en_GB-alan-medium");

        assertNotNull(wav);
        assertEquals(44 + fakePcm.length, wav.length, "WAV = 44-byte header + PCM data");
        // Verify RIFF marker
        assertEquals('R', wav[0]);
        assertEquals('I', wav[1]);
        assertEquals('F', wav[2]);
        assertEquals('F', wav[3]);
        // Verify WAVE marker at offset 8
        assertEquals('W', wav[8]);
        assertEquals('A', wav[9]);
        assertEquals('V', wav[10]);
        assertEquals('E', wav[11]);
    }

    @Test
    void synthesize_emptyText_throwsIllegalArgumentException() {
        PiperTtsClient client = clientFor(true);
        assertThrows(IllegalArgumentException.class, () -> client.synthesize("", "en_GB-alan-medium"));
        assertThrows(IllegalArgumentException.class, () -> client.synthesize("   ", "en_GB-alan-medium"));
        assertThrows(IllegalArgumentException.class, () -> client.synthesize(null, "en_GB-alan-medium"));
    }

    @Test
    void synthesize_piperUnavailable_throwsTtsUnavailableException() {
        // Close the server so no connection can be made
        try { serverSocket.close(); } catch (IOException ignored) {}
        PiperTtsClient client = clientFor(true);
        assertThrows(TtsUnavailableException.class, () -> client.synthesize("Hello", null));
    }

    /** A Piper that accepts the connection (the server socket is open) but never answers. */
    private PiperTtsClient silentPiper() {
        TtsProperties props = new TtsProperties("localhost:" + port, "en_GB-alan-medium", true, List.of("en_GB"));
        Duration shortWait = Duration.ofMillis(300);
        return new PiperTtsClient(props, objectMapper, shortWait, shortWait, shortWait);
    }

    @Test
    void synthesize_piperStopsAnswering_givesUpInsteadOfHanging() {
        PiperTtsClient client = silentPiper();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThrows(TtsUnavailableException.class, () -> client.synthesize("Hello", null));
        });
    }

    @Test
    void describe_piperStopsAnswering_givesUpAndFallsBackToTheDefaultVoice() {
        PiperTtsClient client = silentPiper();

        List<VoiceInfo> voices = assertTimeoutPreemptively(Duration.ofSeconds(5), client::listVoices);

        assertEquals(1, voices.size());
        assertEquals("en_GB-alan-medium", voices.get(0).voiceId());
    }

    @Test
    void synthesize_whenDisabled_throwsTtsUnavailableException() {
        PiperTtsClient client = clientFor(false);
        assertThrows(TtsUnavailableException.class, () -> client.synthesize("Hello", "en_GB-alan-medium"));
    }

    @Test
    void synthesize_correctVoiceName_sendsInWyomingEvent() throws Exception {
        byte[] fakePcm = new byte[]{0x10, 0x20};
        // Capture request in background thread
        final String[] captured = {null};
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                captured[0] = reader.readLine();

                OutputStream rawOut = client.getOutputStream();
                rawOut.write("{\"type\":\"audio-start\",\"data\":{\"rate\":22050,\"width\":2,\"channels\":1}}\n".getBytes(StandardCharsets.UTF_8));
                String chunkHeader = "{\"type\":\"audio-chunk\",\"data\":{},\"payload_length\":" + fakePcm.length + "}\n";
                rawOut.write(chunkHeader.getBytes(StandardCharsets.UTF_8));
                rawOut.write(fakePcm);
                rawOut.write("{\"type\":\"audio-stop\"}\n".getBytes(StandardCharsets.UTF_8));
                rawOut.flush();
            } catch (IOException ignored) {}
        });

        PiperTtsClient client = clientFor(true);
        client.synthesize("Test racer", "en_GB-cori-medium");

        await().atMost(Duration.ofSeconds(5)).until(() -> captured[0] != null);
        assertNotNull(captured[0], "Should have captured the synthesize request");
        assertTrue(captured[0].contains("en_GB-cori-medium"), "Voice name should appear in request");
        assertTrue(captured[0].contains("synthesize"), "Event type should be 'synthesize'");
    }

    @Test
    void assembleWav_validPcm_returns44ByteHeaderPlusPcm() {
        PiperTtsClient client = clientFor(true);
        byte[] pcm = new byte[100];
        byte[] wav = client.assembleWav(pcm, 22050, 2, 1);

        assertEquals(144, wav.length, "Expected 44 header bytes + 100 PCM bytes");

        // Check RIFF/WAVE markers
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'}, new byte[]{wav[0], wav[1], wav[2], wav[3]});
        assertArrayEquals(new byte[]{'W', 'A', 'V', 'E'}, new byte[]{wav[8], wav[9], wav[10], wav[11]});
        assertArrayEquals(new byte[]{'f', 'm', 't', ' '}, new byte[]{wav[12], wav[13], wav[14], wav[15]});
        assertArrayEquals(new byte[]{'d', 'a', 't', 'a'}, new byte[]{wav[36], wav[37], wav[38], wav[39]});
    }

    @Test
    void describe_noVoiceMatchesTheLocales_listsEveryVoiceInsteadOfJustTheDefault() {
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();
                String infoResponse = "{\"type\":\"info\",\"data\":{\"tts\":[{\"name\":\"piper\",\"voices\":[" +
                        "{\"name\":\"en_GB-alan-medium\"},{\"name\":\"en_GB-cori-high\"}]}]}}\n";
                client.getOutputStream().write(infoResponse.getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
            } catch (IOException ignored) {}
        });
        TtsProperties props = new TtsProperties("localhost:" + port, "en_GB-alan-medium", true, List.of("en_US"));
        PiperTtsClient client = new PiperTtsClient(props, objectMapper);

        List<VoiceInfo> voices = client.listVoices();

        assertEquals(List.of("en_GB-alan-medium", "en_GB-cori-high"), voices.stream().map(VoiceInfo::voiceId).toList());
    }

    @Test
    void describe_returnsList_ofAvailableVoices() throws Exception {
        // Wyoming protocol: voices are nested at data.tts[*].voices
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine(); // consume describe request

                String infoResponse = "{\"type\":\"info\",\"data\":{\"tts\":[{\"name\":\"piper\",\"voices\":[" +
                        "{\"name\":\"en_GB-alan-medium\"}," +
                        "{\"name\":\"en_GB-cori-high\"}" +
                        "]}]}}\n";
                client.getOutputStream().write(infoResponse.getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
            } catch (IOException ignored) {}
        });

        PiperTtsClient client = clientFor(true);
        List<VoiceInfo> voices = client.listVoices();

        assertEquals(2, voices.size());
        assertEquals("en_GB-alan-medium", voices.get(0).voiceId());
        assertTrue(voices.get(0).isDefault(), "en_GB-alan-medium should be the default");
        assertFalse(voices.get(1).isDefault());
    }

    @Test
    void describe_piperUnavailable_returnsFallbackDefaultVoice() {
        // Close the server — Piper unreachable
        try { serverSocket.close(); } catch (IOException ignored) {}

        PiperTtsClient client = clientFor(true);
        List<VoiceInfo> voices = client.listVoices();

        assertEquals(1, voices.size(), "Should return fallback default voice when Piper is down");
        assertEquals("en_GB-alan-medium", voices.get(0).voiceId());
        assertTrue(voices.get(0).isDefault());
    }

    @Test
    void describe_whenDisabled_returnsEmptyList() {
        PiperTtsClient client = clientFor(false);
        List<VoiceInfo> voices = client.listVoices();
        assertTrue(voices.isEmpty(), "Disabled TTS should return empty voice list");
    }
}
