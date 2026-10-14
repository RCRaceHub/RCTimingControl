package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays a {@code .dump} file of RC-4 PASSING/STATUS records at configurable speed.
 *
 * <p>Timing is inferred from the {@code timeSinceStart} field deltas between successive
 * PASSING lines. STATUS lines before the first PASSING are spaced like the decoder's 5 s heartbeat;
 * after that they go out straight away, the PASSING lines setting the pace. The
 * {@code speed} factor compresses wall-clock delay: {@code speed=2.0} plays back at 2×.
 */
public class PlaybackMode {

    private static final Logger log = LoggerFactory.getLogger(PlaybackMode.class);

    private PlaybackMode() {}

    /**
     * Replay the dump file to the given output stream.
     *
     * @param out          client output stream
     * @param dumpFilePath path to the {@code .dump} file (file system path, or {@code null} for default)
     * @param speed        playback speed factor ({@code 1.0} = real-time, {@code 2.0} = 2× faster)
     * @throws IOException on I/O error
     */
    public static void replay(OutputStream out, String dumpFilePath, double speed) throws IOException {
        List<String> lines = loadLines(dumpFilePath);
        double prevTime  = -1;

        for (String line : lines) {
            if (Thread.currentThread().isInterrupted()) break;

            // Parse timeSinceStart for PASSING lines to infer delay
            if (line.startsWith("@\t")) {
                String[] f = line.split("\t");
                if (f.length >= 5) {
                    try {
                        double t = Double.parseDouble(f[4]);
                        if (prevTime >= 0) {
                            long delayMs = (long)((t - prevTime) * 1000 / speed);
                            if (delayMs > 0) {
                                Rc4Lines.pause(delayMs);
                            }
                        }
                        prevTime = t;
                    } catch (NumberFormatException ignored) {}
                }
            } else if (line.startsWith("#\t")) {
                // Before the first PASSING, space STATUS lines like the 5 s heartbeat
                if (prevTime < 0) Rc4Lines.pause((long)(5000 / speed));
            }

            Rc4Lines.write(out, line);
            log.debug("[SIMULATOR] Emitting: {}", line);
        }
        log.info("[SIMULATOR] Playback complete");
    }

    private static List<String> loadLines(String dumpFilePath) throws IOException {
        InputStream in;
        if (dumpFilePath != null) {
            in = new FileInputStream(dumpFilePath);
        } else {
            URL resource = PlaybackMode.class.getClassLoader()
                                             .getResource("samples/sample-passings.dump");
            if (resource == null) throw new IOException("Default dump file not found on classpath");
            in = resource.openStream();
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            String l;
            while ((l = reader.readLine()) != null) {
                if (!l.isBlank()) lines.add(l.stripLeading().charAt(0) == 0x01 ? l.substring(1) : l);
            }
        }
        return lines;
    }
}
