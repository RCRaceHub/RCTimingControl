package dev.monkeypatch.rctiming.screens.viewer;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Talks to one EventScreenDisplay agent over its viewer API v1 (#708): the contract in that project's {@code
 * docs/API.md}, which is fixed from this side. Plain blocking HTTP/1.1 with short timeouts (2 s to connect, 4 s to read),
 * so callers run it off the request and timing threads: {@code ScreenMonitor}'s poll threads, or a request thread
 * for a command an official just asked for. Reads never send the token; every {@code PUT}, {@code POST} and {@code
 * DELETE} sends it as a bearer token when the target has one. Any failure is a {@link ViewerException}.
 */
@Component
public class ViewerClient {

    private final RestClient restClient;
    private final ObjectMapper json;

    public ViewerClient(RestClient.Builder builder, ObjectMapper json) {
        // The JDK client rather than HttpURLConnection: that one turns a 401 without a challenge header into an
        // exception on PUT and POST, hiding the status. HTTP/1.1 because the agents are small Python servers
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.json = json;
    }

    /** {@code GET /info}: who the screen is and what it accepts. */
    public ViewerInfo info(ViewerTarget target) {
        return get(target, "/info", ViewerInfo.class);
    }

    /** {@code GET /status}: what is on screen now. */
    public ViewerStatus status(ViewerTarget target) {
        return get(target, "/status", ViewerStatus.class);
    }

    /** {@code PUT /playlist}: replaces the screen's whole playlist and returns it with its new revision. */
    public ViewerPlaylist putPlaylist(ViewerTarget target, ViewerPlaylist.Body body) {
        return send(target, "PUT", "/playlist", body, ViewerPlaylist.class);
    }

    /** {@code POST /control/{action}} for {@code next}, {@code previous}, {@code pause}, {@code resume} or {@code reload}. */
    public ViewerStatus control(ViewerTarget target, String action) {
        return send(target, "POST", "/control/" + action, null, ViewerStatus.class);
    }

    /** {@code POST /control/goto}: jumps to a playlist position; the screen answers 400 when it is out of range. */
    public ViewerStatus goTo(ViewerTarget target, int index) {
        return send(target, "POST", "/control/goto", Map.of("index", index), ViewerStatus.class);
    }

    /** {@code POST /control/show}: a temporary page for {@code duration} seconds, or until cleared when null. */
    public ViewerStatus show(ViewerTarget target, String url, Integer duration, String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (duration != null) {
            body.put("duration", duration);
        }
        if (label != null && !label.isBlank()) {
            body.put("label", label);
        }
        return send(target, "POST", "/control/show", body, ViewerStatus.class);
    }

    /** {@code DELETE /control/show}: ends the temporary page. */
    public ViewerStatus clearShow(ViewerTarget target) {
        return send(target, "DELETE", "/control/show", null, ViewerStatus.class);
    }

    private <T> T get(ViewerTarget target, String path, Class<T> type) {
        try {
            return restClient.get().uri(target.baseUrl() + path).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(type);
        } catch (RestClientResponseException e) {
            throw failed(e);
        } catch (RuntimeException e) {
            throw unreachable(target, e);
        }
    }

    private <T> T send(ViewerTarget target, String method, String path, Object body, Class<T> type) {
        try {
            RestClient.RequestBodySpec request = restClient.method(HttpMethod.valueOf(method))
                    .uri(target.baseUrl() + path)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (target.token() != null && !target.token().isBlank()) {
                            h.setBearerAuth(target.token());
                        }
                    });
            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return request.retrieve().body(type);
        } catch (RestClientResponseException e) {
            throw failed(e);
        } catch (RuntimeException e) {
            throw unreachable(target, e);
        }
    }

    /** The agent answered with an error: its own {@code {"error": "..."}} message when it sent one. */
    private ViewerException failed(RestClientResponseException e) {
        String message = null;
        try {
            JsonNode node = json.readTree(e.getResponseBodyAsString());
            if (node.hasNonNull("error")) {
                message = node.get("error").asString();
            }
        } catch (RuntimeException ignored) {
            // not JSON: fall back to the status
        }
        if (message == null || message.isBlank()) {
            message = "The screen answered " + e.getStatusCode().value();
        }
        return new ViewerException(e.getStatusCode().value(), message, e);
    }

    private static ViewerException unreachable(ViewerTarget target, RuntimeException e) {
        return new ViewerException(null, "Cannot reach the screen at " + target.host() + ":" + target.port(), e);
    }
}
