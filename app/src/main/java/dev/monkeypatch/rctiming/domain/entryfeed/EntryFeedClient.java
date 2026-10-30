package dev.monkeypatch.rctiming.domain.entryfeed;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Downloads an entry feed's file (#42). Never throws: every failure comes back as a {@link Result}, so a feed
 * that can't be reached is shown on the event rather than breaking anything else.
 */
@Component
public class EntryFeedClient {

    private static final int MAX_ERROR_LENGTH = 300;
    /** Far more than any club meeting's entries, which are a few hundred kilobytes at most */
    static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;

    private final RestClient restClient;
    private final int maxBytes;
    private final EntryFeedHeaders extraHeaders;

    @Autowired
    public EntryFeedClient(RestClient.Builder restClientBuilder, ObjectProvider<EntryFeedHeaders> extraHeaders) {
        this(restClientBuilder, DEFAULT_MAX_BYTES, extraHeaders.getIfAvailable(() -> url -> Map.of()));
    }

    public EntryFeedClient(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, DEFAULT_MAX_BYTES);
    }

    EntryFeedClient(RestClient.Builder restClientBuilder, int maxBytes) {
        this(restClientBuilder, maxBytes, url -> Map.of());
    }

    EntryFeedClient(RestClient.Builder restClientBuilder, int maxBytes, EntryFeedHeaders extraHeaders) {
        this.maxBytes = maxBytes;
        this.extraHeaders = extraHeaders;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    /** The outcome of a fetch: the file, a refused token, or any other failure with a message an official can read. */
    public sealed interface Result {
    }

    public record Fetched(String body) implements Result {
    }

    public record AuthFailed(String message) implements Result {
    }

    public record Failed(String message) implements Result {
    }

    /**
     * Fetches {@code url}, sending {@code token} (if any) as a bearer token, plus any headers {@link EntryFeedHeaders}
     * names for that address (a Cloudflare Access service token for RaceHub's host). Blocks for up to 5 seconds to connect
     * and 20 to read, so callers keep it outside any database transaction. The body is read up to a size limit
     * (5 MB by default), and a longer answer is a {@link Failed}.
     */
    public Result fetch(URI url, String token) {
        try {
            return restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (token != null) {
                            h.setBearerAuth(token);
                        }
                        extraHeaders.headersFor(url).forEach(h::set);
                    })
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                            return new AuthFailed("The feed refused the token (" + status + "). Check the token and save it again.");
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            byte[] start = response.getBody().readNBytes(MAX_ERROR_LENGTH);
                            return new Failed(shorten("The feed answered " + status + ": "
                                    + new String(start, StandardCharsets.UTF_8)));
                        }
                        return read(response.getBody());
                    });
        } catch (RuntimeException e) {
            return new Failed(shorten("Couldn't reach the feed: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    // Reads at most maxBytes, so an unexpectedly large answer can't fill the laptop's memory during a meeting
    private Result read(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            return new Failed("The feed's file is larger than " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.isBlank() ? new Failed("The feed answered with an empty file") : new Fetched(text);
    }

    private static String shorten(String message) {
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
