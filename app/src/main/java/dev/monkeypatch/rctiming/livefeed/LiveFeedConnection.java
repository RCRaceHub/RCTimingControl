package dev.monkeypatch.rctiming.livefeed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The one outbound WebSocket to the relay (#28). Opened and sent on only from the live feed's own thread, so
 * sends never overlap. Any failure closes it; the caller decides when to open it again.
 *
 * <p>{@link #close} is the exception: {@code LiveFeedPublisher.stop} calls it from whichever thread stops the
 * app, which is why the socket itself is volatile.
 */
class LiveFeedConnection {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedConnection.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final long SEND_TIMEOUT_SECONDS = 5;
    private static final long CLOSE_TIMEOUT_SECONDS = 1;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private volatile WebSocket webSocket;

    boolean isOpen() {
        WebSocket ws = webSocket;
        return ws != null && !ws.isOutputClosed() && !ws.isInputClosed();
    }

    /** Opens the connection, waiting at most a few seconds. Throws if the relay can't be reached or refuses. */
    void open(URI relayUrl, String token) throws Exception {
        close();
        webSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .buildAsync(relayUrl, new Listener())
                .get(CONNECT_TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS);
    }

    /** Sends one message, waiting at most a few seconds. Throws, and closes, if it can't. */
    void send(String text) throws Exception {
        WebSocket ws = webSocket;
        if (ws == null) {
            throw new IllegalStateException("Not connected to the relay");
        }
        try {
            ws.sendText(text, true).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    /**
     * Says goodbye to the relay and drops the connection. The close frame is given a moment to go out before
     * the socket is cut: aborting at once, as this once did, meant the relay never saw it and logged a
     * dropped connection. It waits at most a second, on the live feed's own thread.
     */
    void close() {
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws == null) {
            return;
        }
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Already gone, or the relay did not take it in time: either way the socket is dropped below
        }
        ws.abort();
    }

    /** The relay sends nothing the feed needs; keep reading so pings are answered, and note a close. */
    private final class Listener implements WebSocket.Listener {
        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("Live feed relay closed the connection ({} {})", statusCode, reason);
            if (webSocket == ws) {
                webSocket = null;
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.info("Live feed connection to the relay failed: {}", error.toString());
            if (webSocket == ws) {
                webSocket = null;
            }
        }
    }
}
