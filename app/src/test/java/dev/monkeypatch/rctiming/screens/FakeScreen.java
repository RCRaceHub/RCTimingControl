package dev.monkeypatch.rctiming.screens;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for an EventScreenDisplay agent for tests (#708): serves just enough of viewer API v1 on a loopback port,
 * from the payloads in that project's {@code docs/API.md}. Changes need the bearer token when one is set, as a real
 * agent does. {@link #calls()} records each request as {@code METHOD path}, with {@code (auth)} when a bearer token came.
 */
public final class FakeScreen implements AutoCloseable {

    private static final Pattern SOURCE = Pattern.compile("\"source\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpServer server;
    private final String id;
    private final String token;
    private final AtomicLong revision = new AtomicLong(1);
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile String source = "";
    private volatile int length;
    private volatile String state = "idle";
    private volatile String showUrl;
    private volatile String lastBody = "";

    public FakeScreen(String id, String token) {
        this.id = id;
        this.token = token;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/api/v1", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public List<String> calls() {
        return calls;
    }

    public String lastBody() {
        return lastBody;
    }

    /** Changes the playlist behind the manager's back, as someone using the agent's own API would. */
    public void changeElsewhere() {
        revision.incrementAndGet();
        source = "someone-else";
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring("/api/v1".length());
        String method = ex.getRequestMethod();
        boolean change = !method.equals("GET");
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        calls.add(method + " " + path + (auth != null ? " (auth)" : ""));
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (change && token != null && !("Bearer " + token).equals(auth)) {
            reply(ex, 401, "{\"error\": \"missing or wrong token\"}");
            return;
        }
        switch (method + " " + path) {
            case "GET /info" -> reply(ex, 200, "{\"id\":\"" + id + "\",\"name\":\"Screen " + id + "\",\"hostname\":\"" + id
                    + "\",\"version\":\"0.1.0\",\"api_version\":\"1\",\"browser\":\"dummy\",\"auth_required\":"
                    + (token != null) + ",\"limits\":{\"default_duration\":30,\"min_duration\":5},\"future\":1}");
            case "GET /status" -> reply(ex, 200, status());
            case "PUT /playlist" -> {
                lastBody = body;
                Matcher m = SOURCE.matcher(body);
                source = m.find() ? m.group(1) : "";
                length = body.split("\"url\"", -1).length - 1;
                if (length == 0) {
                    reply(ex, 400, "{\"error\": \"items must not be empty\"}");
                    return;
                }
                state = "playing";
                long rev = revision.incrementAndGet();
                reply(ex, 200, "{\"items\":[],\"revision\":" + rev + ",\"updated_at\":1.0,\"source\":\"" + source + "\"}");
            }
            case "POST /control/show" -> {
                lastBody = body;
                showUrl = body.contains("\"url\"") ? body.replaceAll(".*\"url\"\\s*:\\s*\"([^\"]*)\".*", "$1") : null;
                state = "override";
                reply(ex, 200, status());
            }
            case "DELETE /control/show" -> {
                showUrl = null;
                state = length > 0 ? "playing" : "idle";
                reply(ex, 200, status());
            }
            case "POST /control/goto" -> {
                lastBody = body;
                reply(ex, body.contains("99") ? 400 : 200, body.contains("99") ? "{\"error\": \"index out of range\"}" : status());
            }
            case "POST /control/next", "POST /control/previous", "POST /control/pause", "POST /control/resume",
                 "POST /control/reload" -> {
                if (path.endsWith("pause")) {
                    state = "paused";
                } else if (path.endsWith("resume")) {
                    state = length > 0 ? "playing" : "idle";
                }
                reply(ex, 200, status());
            }
            default -> reply(ex, 404, "{\"error\": \"not found\"}");
        }
    }

    private String status() {
        double now = System.currentTimeMillis() / 1000.0;
        String override = showUrl == null ? "null" : "{\"url\":\"" + showUrl + "\",\"until\":null}";
        return "{\"id\":\"" + id + "\",\"uptime\":10.0,\"time\":" + now + ",\"state\":\"" + state + "\",\"paused\":"
                + state.equals("paused") + ",\"index\":" + (length > 0 ? 0 : "null") + ",\"current\":null,\"override\":"
                + override + ",\"network\":{\"page_reachable\":true,\"checked_at\":" + now + "},\"playlist\":{\"revision\":"
                + revision.get() + ",\"length\":" + length + ",\"updated_at\":1.0,\"source\":\"" + source
                + "\"},\"browser\":{\"driver\":\"dummy\",\"running\":true,\"pid\":1,\"url\":\"\",\"restarts\":0,\"last_error\":\"\"}}";
    }

    private static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
