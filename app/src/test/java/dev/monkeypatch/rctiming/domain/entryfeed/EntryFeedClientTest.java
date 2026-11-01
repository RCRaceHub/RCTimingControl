package dev.monkeypatch.rctiming.domain.entryfeed;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The feed client reads at most a set size, so an unexpectedly large answer can't fill the laptop's memory. */
class EntryFeedClientTest {

    private HttpServer server;
    private volatile String body;
    private volatile int status = 200;
    private volatile com.sun.net.httpserver.Headers seen;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/entries", exchange -> {
            seen = exchange.getRequestHeaders();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // The client stops reading once the file is too large
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aFileWithinTheLimit_isFetched() {
        body = "{\"revision\":1}";

        assertThat(client(1024).fetch(url(), null))
                .isEqualTo(new EntryFeedClient.Fetched("{\"revision\":1}"));
    }

    @Test
    void aFileOverTheLimit_failsWithoutReadingItAll() {
        body = "x".repeat(4096);

        EntryFeedClient.Result result = client(1024).fetch(url(), null);

        assertThat(result).isInstanceOf(EntryFeedClient.Failed.class);
        assertThat(((EntryFeedClient.Failed) result).message()).contains("larger than");
    }

    @Test
    void anErrorAnswer_quotesOnlyTheStartOfItsBody() {
        status = 500;
        body = "boom ".repeat(1000);

        EntryFeedClient.Result result = client(1024).fetch(url(), null);

        assertThat(result).isInstanceOf(EntryFeedClient.Failed.class);
        String message = ((EntryFeedClient.Failed) result).message();
        assertThat(message).startsWith("The feed answered 500: boom");
        assertThat(message.length()).isLessThanOrEqualTo(300);
    }

    private EntryFeedClient client(int maxBytes) {
        return new EntryFeedClient(RestClient.builder(), maxBytes);
    }

    private URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/entries");
    }

    @Test
    void sendsTheExtraHeadersTheProviderNamesForTheAddressAlongsideTheBearerToken() {
        body = "{\"revision\":1}";
        EntryFeedClient withHeaders = new EntryFeedClient(RestClient.builder(), 1024,
                address -> Map.of("CF-Access-Client-Id", "id", "CF-Access-Client-Secret", "secret"));

        assertThat(withHeaders.fetch(url(), "key")).isInstanceOf(EntryFeedClient.Fetched.class);

        assertThat(seen.getFirst("CF-Access-Client-Id")).isEqualTo("id");
        assertThat(seen.getFirst("CF-Access-Client-Secret")).isEqualTo("secret");
        assertThat(seen.getFirst("Authorization")).isEqualTo("Bearer key");
    }

    @Test
    void sendsNoExtraHeadersWhenNoneAreNamed() {
        body = "{\"revision\":1}";

        client(1024).fetch(url(), "key");

        assertThat(seen.getFirst("CF-Access-Client-Id")).isNull();
        assertThat(seen.getFirst("CF-Access-Client-Secret")).isNull();
    }
}
