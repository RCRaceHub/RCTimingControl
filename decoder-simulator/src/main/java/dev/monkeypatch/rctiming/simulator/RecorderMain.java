package dev.monkeypatch.rctiming.simulator;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Entry point for {@code RCTimingControl record}: records a real RC-4 decoder to a {@code .dump} file.
 *
 * <pre>
 * RCTimingControl record --host=192.168.1.50 [--port=5100] [--out=capture.dump]
 * </pre>
 *
 * <p>{@code --out} defaults to {@code decoder-yyyyMMdd-HHmmss.dump} in the working folder. Stop it with Ctrl+C. Bad
 * flags print usage and exit with status 2; a file that cannot be written ends it with a stack trace and
 * status 1. Replay the result with {@code simulate --mode=playback --file=...}.
 */
public class RecorderMain {

    private RecorderMain() {
    }

    /** Parses the flags and records until the process is stopped. */
    public static void main(String[] args) throws IOException {
        Map<String, String> flags = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--")) {
                usage("Unknown argument: " + arg);
            }
            flags.put(eq < 0 ? arg : arg.substring(0, eq), eq < 0 ? "true" : arg.substring(eq + 1));
        }
        String host = flags.get("--host");
        if (host == null || host.isBlank()) {
            usage("--host is required");
        }
        flags.keySet().stream().filter(k -> !Set.of("--host", "--port", "--out").contains(k))
                .findFirst().ifPresent(k -> usage("Unknown flag: " + k));
        int port = 0;
        try {
            port = Integer.parseInt(flags.getOrDefault("--port", "5100"));
        } catch (NumberFormatException e) {
            usage("--port must be an integer");
        }
        if (port < 1 || port > 65535) {
            usage("--port must be between 1 and 65535");
        }
        Path out = null;
        try {
            out = Path.of(flags.getOrDefault("--out",
                    "decoder-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".dump"));
        } catch (InvalidPathException e) {
            usage("--out is not a valid file name");
        }

        DecoderRecorder recorder = new DecoderRecorder(host, port, out);
        Thread worker = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            recorder.stop();
            worker.interrupt();
            try {
                worker.join(5_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        System.out.println("Recording " + host + ":" + port + " to " + out + " (Ctrl+C to stop)");
        recorder.run();
    }

    private static void usage(String error) {
        System.err.println("Error: " + error);
        System.err.println("""
            Usage:
              record --host=192.168.1.50 [--port=5100] [--out=capture.dump]
            """);
        System.exit(2);
    }
}
