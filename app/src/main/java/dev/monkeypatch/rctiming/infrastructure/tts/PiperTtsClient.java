package dev.monkeypatch.rctiming.infrastructure.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Client for Piper TTS via Wyoming TCP protocol (port 10200).
 * <p>
 * Protocol exchange:
 * <pre>
 *   → {"type":"synthesize","data":{"text":"...","voice":{"name":"..."}}}\n
 *   ← {"type":"audio-start","data":{"rate":22050,"width":2,"channels":1}}\n
 *   ← {"type":"audio-chunk","data":{...},"payload_length":N}\n  [N bytes raw PCM]
 *   ← ... (repeated)
 *   ← {"type":"audio-stop"}\n
 * </pre>
 *
 * <p>Called from HTTP threads (the voice list, a spoken-name preview), so no call waits for ever on a Piper
 * that is not there or has stopped answering: connecting is given {@link #CONNECT_TIMEOUT}, and each wait for
 * Piper's next bytes is given a read timeout (longer for synthesis, which can take a few seconds to start).
 */
@Component
public class PiperTtsClient {

    private static final Logger log = LoggerFactory.getLogger(PiperTtsClient.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    /** Longest wait for any one reply while listing voices. */
    static final Duration DESCRIBE_READ_TIMEOUT = Duration.ofSeconds(5);
    /** Longest wait for any one piece of audio while synthesizing. */
    static final Duration SYNTHESIZE_READ_TIMEOUT = Duration.ofSeconds(30);

    private final TtsProperties properties;
    private final ObjectMapper objectMapper;
    private final Duration connectTimeout;
    private final Duration describeReadTimeout;
    private final Duration synthesizeReadTimeout;

    @Autowired
    public PiperTtsClient(TtsProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, CONNECT_TIMEOUT, DESCRIBE_READ_TIMEOUT, SYNTHESIZE_READ_TIMEOUT);
    }

    /** With the waits chosen, so a test need not wait the real ones out. */
    public PiperTtsClient(TtsProperties properties, ObjectMapper objectMapper, Duration connectTimeout,
                   Duration describeReadTimeout, Duration synthesizeReadTimeout) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.connectTimeout = connectTimeout;
        this.describeReadTimeout = describeReadTimeout;
        this.synthesizeReadTimeout = synthesizeReadTimeout;
    }

    /** Connects to the configured Piper; waits for each reply at most {@code readTimeout}. */
    private Socket connect(Duration readTimeout) throws IOException {
        String[] hostPort = splitHostPort(properties.endpoint());
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])),
                    (int) connectTimeout.toMillis());
            socket.setSoTimeout((int) readTimeout.toMillis());
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Synthesize text to WAV bytes using Piper Wyoming protocol.
     *
     * @param text      text to synthesize
     * @param voiceName Piper voice model name (e.g. "en_GB-alan-medium"), or null to use default
     * @return WAV bytes (44-byte header + PCM data)
     * @throws TtsUnavailableException if TTS is disabled or connection fails
     * @throws IllegalArgumentException if text is null or blank
     */
    public byte[] synthesize(String text, String voiceName) {
        if (!properties.enabled()) {
            throw new TtsUnavailableException("TTS is disabled");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }

        String effectiveVoice = (voiceName != null && !voiceName.isBlank())
                ? voiceName
                : properties.defaultVoice();

        try (Socket socket = connect(synthesizeReadTimeout)) {
            OutputStream rawOut = socket.getOutputStream();
            // Buffered: the reply is read a byte at a time looking for the end of each line
            InputStream rawIn = new BufferedInputStream(socket.getInputStream());

            // Send synthesize event as JSONL
            Map<String, Object> event = Map.of(
                    "type", "synthesize",
                    "data", Map.of(
                            "text", text,
                            "voice", Map.of("name", effectiveVoice)
                    )
            );
            String json = objectMapper.writeValueAsString(event) + "\n";
            rawOut.write(json.getBytes(StandardCharsets.UTF_8));
            rawOut.flush();

            // Read response frames
            ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
            int sampleRate = 22050;
            int sampleWidth = 2;
            int channels = 1;

            while (true) {
                String line = readJsonLine(rawIn);
                if (line == null || line.isBlank()) {
                    break;
                }

                JsonNode header = objectMapper.readTree(line);
                String type = header.get("type").asText();

                // Wyoming protocol: data_length bytes of JSON payload follow the header line
                int dataLength = header.path("data_length").asInt(0);
                JsonNode data = dataLength > 0
                        ? objectMapper.readTree(rawIn.readNBytes(dataLength))
                        : header.path("data");

                switch (type) {
                    case "audio-start" -> {
                        sampleRate = data.path("rate").asInt(22050);
                        sampleWidth = data.path("width").asInt(2);
                        channels = data.path("channels").asInt(1);
                    }
                    case "audio-chunk" -> {
                        int payloadLength = header.path("payload_length").asInt(0);
                        if (payloadLength > 0) {
                            byte[] payload = rawIn.readNBytes(payloadLength);
                            pcmBuffer.write(payload);
                        }
                    }
                    case "audio-stop" -> {
                        // Done
                    }
                    default -> log.debug("Ignoring unknown Wyoming event type: {}", type);
                }

                if ("audio-stop".equals(type)) {
                    break;
                }
            }

            return assembleWav(pcmBuffer.toByteArray(), sampleRate, sampleWidth, channels);

        } catch (IOException e) {
            throw new TtsUnavailableException("Piper TTS at " + properties.endpoint()
                    + " could not be reached or stopped answering: " + e.getMessage(), e);
        }
    }

    /**
     * List available voices by sending a describe event to Piper.
     * Lists the voices matching the configured locales, or every voice if none match. Falls back to a
     * single-entry list using the configured default voice if Piper is unreachable or returns no voices
     * (e.g. still downloading the model).
     */
    public List<VoiceInfo> listVoices() {
        if (!properties.enabled()) {
            return Collections.emptyList();
        }

        try (Socket socket = connect(describeReadTimeout)) {
            OutputStream rawOut = socket.getOutputStream();
            InputStream rawIn = new BufferedInputStream(socket.getInputStream());

            // Send describe event
            String json = objectMapper.writeValueAsString(Map.of("type", "describe")) + "\n";
            rawOut.write(json.getBytes(StandardCharsets.UTF_8));
            rawOut.flush();

            // Read info response — Wyoming protocol: data_length bytes carry the JSON payload
            String line = readJsonLine(rawIn);
            if (line != null && !line.isBlank()) {
                JsonNode header = objectMapper.readTree(line);
                if ("info".equals(header.path("type").asText())) {
                    int dataLength = header.path("data_length").asInt(0);
                    JsonNode data = dataLength > 0
                            ? objectMapper.readTree(rawIn.readNBytes(dataLength))
                            : header.path("data");

                    List<String> effectiveLocales = properties.effectiveLocales();
                    List<VoiceInfo> voices = new ArrayList<>();
                    List<VoiceInfo> otherLocales = new ArrayList<>();
                    JsonNode ttsArray = data.path("tts");
                    if (ttsArray.isArray()) {
                        for (JsonNode ttsProgram : ttsArray) {
                            JsonNode voicesNode = ttsProgram.path("voices");
                            if (voicesNode.isArray()) {
                                for (JsonNode v : voicesNode) {
                                    String name = v.path("name").asText();
                                    if (name.isBlank()) {
                                        continue;
                                    }
                                    boolean isDefault = name.equals(properties.defaultVoice());
                                    VoiceInfo voice = new VoiceInfo(name, formatVoiceLabel(name), isDefault);
                                    (matchesLocale(name, effectiveLocales) ? voices : otherLocales).add(voice);
                                }
                            }
                        }
                    }
                    if (!voices.isEmpty()) {
                        return voices;
                    }
                    // The locale comes from the JVM when tts.locales is unset, so a machine set to en_US would
                    // otherwise hide every en_GB voice and leave the picker with just the default
                    if (!otherLocales.isEmpty()) {
                        log.warn("No Piper voices matched locales {}; listing all {} voices",
                                effectiveLocales, otherLocales.size());
                        return otherLocales;
                    }
                    log.warn("Piper listed no voices; falling back to default voice");
                }
            }

        } catch (IOException e) {
            log.warn("Failed to list Piper voices from {}: {}", properties.endpoint(), e.getMessage());
        }

        // Fallback: return the configured default voice so the UI is never empty
        return List.of(new VoiceInfo(
                properties.defaultVoice(),
                formatVoiceLabel(properties.defaultVoice()),
                true));
    }

    /**
     * Assemble a WAV file from raw PCM data. Public so the test in the audio package can check the header.
     */
    public byte[] assembleWav(byte[] pcmData, int sampleRate, int sampleWidth, int channels) {
        int byteRate = sampleRate * channels * sampleWidth;
        int blockAlign = channels * sampleWidth;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(chunkSize);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);                          // PCM subchunk1 size
        header.putShort((short) 1);                 // audio format: PCM
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) (sampleWidth * 8)); // bits per sample
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(dataSize);

        byte[] result = new byte[44 + dataSize];
        System.arraycopy(header.array(), 0, result, 0, 44);
        System.arraycopy(pcmData, 0, result, 44, dataSize);
        return result;
    }

    /**
     * Read a single newline-terminated UTF-8 line from the stream.
     * Does NOT consume binary payload bytes — caller must do that separately
     * when payload_length > 0.
     */
    private String readJsonLine(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') {
                break;
            }
            bos.write(b);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Split "host:port" string into [host, port].
     * Defaults to port 10200 if not specified.
     */
    private String[] splitHostPort(String endpoint) {
        int lastColon = endpoint.lastIndexOf(':');
        if (lastColon > 0) {
            return new String[]{endpoint.substring(0, lastColon), endpoint.substring(lastColon + 1)};
        }
        return new String[]{endpoint, "10200"};
    }

    /** Returns true if the voice name's locale prefix (e.g. "en_GB") is in the allowed list. */
    private boolean matchesLocale(String voiceName, List<String> locales) {
        int dash = voiceName.indexOf('-');
        String voiceLocale = dash > 0 ? voiceName.substring(0, dash) : voiceName;
        return locales.stream().anyMatch(l -> l.equalsIgnoreCase(voiceLocale));
    }

    private String formatVoiceLabel(String voiceId) {
        // Convert "en_GB-alan-medium" → "Alan (en-GB, medium)"
        String[] parts = voiceId.split("-");
        if (parts.length >= 2) {
            String lang = parts[0].replace("_", "-");
            String name = Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1);
            String quality = parts.length >= 3 ? ", " + parts[2] : "";
            return name + " (" + lang + quality + ")";
        }
        return voiceId;
    }
}
