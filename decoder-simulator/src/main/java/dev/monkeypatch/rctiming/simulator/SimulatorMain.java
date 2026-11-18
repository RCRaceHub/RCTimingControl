package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Entry point for the standalone AMB RC-4 TCP decoder simulator.
 *
 * <h3>Usage</h3>
 * <pre>
 * ./gradlew :decoder-simulator:runSimulator --args="--mode=playback --port=5100 --speed=1.0"
 * ./gradlew :decoder-simulator:runSimulator --args="--mode=generative --port=5100 --transponders=11111,22222,33333 --interval-ms=12500 --jitter-ms=2500"
 * </pre>
 *
 * <h3>Flags</h3>
 * <ul>
 *   <li>{@code --mode} — {@code playback}, {@code generative} or {@code track} (required)</li>
 *   <li>{@code --port} — TCP port to listen on (default: 5100)</li>
 *   <li>{@code --speed} — playback speed factor (default: 1.0; playback mode only)</li>
 *   <li>{@code --file} — the {@code .dump} file to replay (default: the bundled sample; playback mode only)</li>
 *   <li>{@code --transponders} — comma-separated transponder IDs (default: 11111,22222; generative mode only)</li>
 *   <li>{@code --interval-ms} — base lap time in milliseconds (default: 12500; generative mode only)</li>
 *   <li>{@code --jitter-ms} — spread of the per-lap noise, with per-driver pace and occasional slow laps on top (default: 2500; generative only)</li>
 *   <li>{@code --split-ports} — {@code track} mode: comma-separated ports, one fake split decoder each, in track order;
 *       {@code --port} is the start/finish decoder (default: none)</li>
 *   <li>{@code --split-positions} — {@code track} mode: where each split is, as a share of the lap after the finish
 *       loop, such as {@code 0.35,0.7} (default: spread evenly)</li>
 *   <li>{@code --split-delay-ms} — {@code track} mode: how long each split decoder holds its records back, to fake a
 *       clock offset against the finish decoder, such as {@code 25,-0} (default: 0 each; the finish decoder is never
 *       delayed)</li>
 * </ul>
 *
 * <p>Invalid or missing required arguments cause usage to be printed to stderr and exit code 2.
 */
public class SimulatorMain {

    private static final Logger log = LoggerFactory.getLogger(SimulatorMain.class);

    /**
     * Parses the flags, starts the chosen mode and blocks until the process is stopped; a shutdown hook stops the
     * server. Bad flags print usage and exit with status 2.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Map<String, String> flags = parseFlags(args);

        String mode = flags.get("--mode");
        if (mode == null) {
            printUsageAndExit("--mode is required");
        }

        int    port       = parseFlag(flags, "--port",        5100,    Integer::parseInt,   "an integer");
        double speed      = parseFlag(flags, "--speed",       1.0,     Double::parseDouble, "a number");
        long   intervalMs = parseFlag(flags, "--interval-ms", 12_500L, Long::parseLong,     "a long integer");
        long   jitterMs   = parseFlag(flags, "--jitter-ms",   2_500L,  Long::parseLong,     "a long integer");

        List<String> transponders = new ArrayList<>();
        String txpRaw = flags.getOrDefault("--transponders", "11111,22222");
        for (String t : txpRaw.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) transponders.add(trimmed);
        }

        List<FakeDecoderServer> servers = new ArrayList<>();
        switch (mode) {
            case "playback"   -> servers.add(FakeDecoderServer.playback(port, flags.get("--file"), speed));
            case "generative" -> servers.add(FakeDecoderServer.generative(port, transponders, intervalMs, jitterMs));
            case "track"      -> servers.addAll(trackServers(flags, port, transponders, intervalMs, jitterMs));
            default -> { printUsageAndExit("Unknown --mode: " + mode); return; }
        }

        for (FakeDecoderServer server : servers) {
            server.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> servers.forEach(FakeDecoderServer::stop)));
        log.info("[SIMULATOR] Running — Ctrl-C to stop");
        Thread.currentThread().join(); // block forever
    }

    /**
     * The servers for {@code track} mode: the finish decoder on {@code port} and one per {@code --split-ports} entry.
     * Exits with usage if the lists disagree in length or a position is not strictly between 0 and 1 and rising.
     */
    private static List<FakeDecoderServer> trackServers(Map<String, String> flags, int port,
                                                        List<String> transponders, long intervalMs, long jitterMs) {
        int[] ports = parseList(flags.get("--split-ports"), Integer::parseInt).stream().mapToInt(i -> i).toArray();
        double[] positions;
        if (flags.containsKey("--split-positions")) {
            positions = parseList(flags.get("--split-positions"), Double::parseDouble)
                    .stream().mapToDouble(d -> d).toArray();
        } else {
            positions = new double[ports.length];
            for (int i = 0; i < ports.length; i++) {
                positions[i] = (i + 1) / (double) (ports.length + 1);
            }
        }
        List<Long> delays = flags.containsKey("--split-delay-ms")
                ? parseList(flags.get("--split-delay-ms"), Long::parseLong)
                : java.util.Collections.nCopies(ports.length, 0L);
        if (positions.length != ports.length || delays.size() != ports.length) {
            printUsageAndExit("--split-ports, --split-positions and --split-delay-ms need the same number of entries");
        }
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] <= 0 || positions[i] >= 1 || (i > 0 && positions[i] <= positions[i - 1])) {
                printUsageAndExit("--split-positions must rise and stay between 0 and 1");
            }
        }
        TrackSimulation track = new TrackSimulation(transponders, intervalMs, jitterMs, positions,
                System.currentTimeMillis(), System.nanoTime());
        List<FakeDecoderServer> servers = new ArrayList<>();
        servers.add(FakeDecoderServer.trackLoop(port, track, 0, 0));
        for (int i = 0; i < ports.length; i++) {
            servers.add(FakeDecoderServer.trackLoop(ports[i], track, i + 1, delays.get(i)));
        }
        return servers;
    }

    /** A comma-separated list read with {@code parse}; empty when absent. Exits with usage if an entry is unreadable. */
    private static <T> List<T> parseList(String raw, Function<String, T> parse) {
        List<T> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                values.add(parse.apply(part.trim()));
            } catch (NumberFormatException e) {
                printUsageAndExit("Not a number: " + part);
            }
        }
        return values;
    }

    private static Map<String, String> parseFlags(String[] args) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                System.err.println("Unknown argument: " + arg);
                printUsageAndExit(null);
            }
            int eq = arg.indexOf('=');
            if (eq < 0) {
                map.put(arg, "true");
            } else {
                map.put(arg.substring(0, eq), arg.substring(eq + 1));
            }
        }
        return map;
    }

    /** The flag's value read by {@code parse}, or {@code def} when it isn't given; exits with usage if unreadable. */
    private static <T> T parseFlag(Map<String, String> flags, String key, T def, Function<String, T> parse,
                                   String what) {
        try { return flags.containsKey(key) ? parse.apply(flags.get(key)) : def; }
        catch (NumberFormatException e) { printUsageAndExit(key + " must be " + what); return def; }
    }

    private static void printUsageAndExit(String error) {
        if (error != null) System.err.println("Error: " + error);
        System.err.println("""
            Usage:
              --mode=playback|generative|track  (required)
              --port=5100                 TCP port (default: 5100)
              --speed=1.0                 Playback speed factor (playback only)
              --file=/path/to/dump        Dump file path (playback only; default: classpath sample)
              --transponders=11111,22222  Transponder IDs (generative only)
              --interval-ms=12500         Base lap time in ms (default: 12500)
              --jitter-ms=2500            Lap-time noise spread ms (default: 2500)
              --split-ports=5101,5102     Track mode: one fake split decoder per port; --port is the finish decoder
              --split-positions=0.35,0.7  Track mode: each split's share of the lap (default: evenly spread)
              --split-delay-ms=0,25       Track mode: hold each split decoder's records back, to fake a clock offset
            """);
        System.exit(2);
    }
}
