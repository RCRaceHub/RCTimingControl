package dev.monkeypatch.rctiming.simulator.relay;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs the live feed test relay (#28) until stopped.
 *
 * <pre>
 * ./gradlew :decoder-simulator:runRelay --args="--port=8099 --token=dev-relay-token"
 * RCTimingControl relay --port=8099 --token=dev-relay-token
 * </pre>
 *
 * Then point the app at it with {@code rctiming.livefeed.relay-url=ws://localhost:8099/publish} and
 * {@code rctiming.livefeed.token=dev-relay-token}, and open {@code http://localhost:8099/} to watch.
 */
public final class LiveFeedRelayMain {

    private LiveFeedRelayMain() {
    }

    /**
     * Starts a {@link LiveFeedTestRelay} on {@code --port} (default 8099), checking publishers against
     * {@code --token} (default {@code dev-relay-token}), bound to {@code --bind} (default 127.0.0.1, so only this
     * machine can reach it). Prints the app settings and viewer URL, then blocks until stopped. A malformed
     * argument prints usage and exits with status 2.
     */
    public static void main(String[] args) throws InterruptedException {
        Map<String, String> flags = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--") || eq < 0) {
                System.err.println("Usage: relay [--port=8099] [--token=dev-relay-token] [--bind=127.0.0.1]");
                System.exit(2);
            }
            flags.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        int port = Integer.parseInt(flags.getOrDefault("--port", "8099"));
        String token = flags.getOrDefault("--token", "dev-relay-token");
        String bind = flags.getOrDefault("--bind", "127.0.0.1");

        LiveFeedTestRelay relay = new LiveFeedTestRelay(port, token);
        int bound = relay.start(bind);
        Runtime.getRuntime().addShutdownHook(new Thread(relay::close));
        System.out.printf("Live feed test relay on port %d%n", bound);
        System.out.printf("  App settings: rctiming.livefeed.relay-url=ws://localhost:%d/publish%n", bound);
        System.out.printf("                rctiming.livefeed.token=%s%n", token);
        System.out.printf("  Viewer page:  http://localhost:%d/%n", bound);
        Thread.currentThread().join();
    }
}
