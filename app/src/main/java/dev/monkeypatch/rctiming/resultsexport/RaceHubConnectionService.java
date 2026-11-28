package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubResultsProperties;
import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Whether the link to RaceHub works, for the admin's "Test connection" button and the race director's status
 * indicator. RaceHub has no ping or "who am I" route, so this uses what the club's key already does:
 * <ol>
 *   <li>{@code GET /api/rctc/v1/events?state=ready}, a cheap authenticated read. It shows the host is reachable and
 *       the answer is RaceHub's, whether the key is accepted, and (the route needs {@code entries:read}) that the key
 *       can read.</li>
 *   <li>{@code POST /api/rctc/v1/results} with an empty JSON object as the body, which RaceHub's route guards with
 *       {@code results:write} before it checks the body. A {@code 422} (the empty object is refused) therefore proves
 *       the key may send results without storing anything; a {@code 403} says it may not. Only the admin's test makes
 *       this call, never the background check.</li>
 * </ol>
 *
 * <p>The background check ({@link #refresh}) runs the first call every minute on a scheduler thread, only while an
 * address and key are both set, and keeps the answer for {@link #status()}. That is well inside RaceHub's limit of 30
 * calls a minute per key. Both calls block for up to the connect and read timeouts, so neither runs in a transaction.
 * Nothing is logged or returned that holds the key.
 */
@Service
public class RaceHubConnectionService {

    private static final Logger log = LoggerFactory.getLogger(RaceHubConnectionService.class);

    /** The state of one thing checked. */
    public enum CheckState {
        /** Worked. */
        PASS,
        /** Did not work; {@code detail} says why and what to do. */
        FAIL,
        /** RaceHub did not give a clear answer (for example it asked us to slow down). */
        UNKNOWN,
        /** Not tried, because an earlier check failed. */
        SKIPPED
    }

    /** One line of the test. {@code id} is one of {@code address}, {@code key}, {@code reach}, {@code read}, {@code write}. */
    public record Check(String id, String label, CheckState state, String detail) {
    }

    /**
     * The result of one run.
     *
     * @param ok          whether every check passed
     * @param summary     one sentence for the top of the result
     * @param checkedAt   when it ran
     * @param readyEvents how many meetings RaceHub lists as released to timing, or null if not read
     * @param checks      each check in order
     */
    public record TestResult(boolean ok, String summary, Instant checkedAt, Integer readyEvents, List<Check> checks) {
    }

    /** The link's health as the director's indicator shows it. */
    public enum Health {
        /** No address or no key: a standalone club, so the indicator is hidden. */
        NOT_CONFIGURED,
        /** Configured but not checked yet (just started, or the address or key just changed). */
        CHECKING,
        /** RaceHub answered and accepted the key. */
        OK,
        /** RaceHub could not be reached. */
        UNREACHABLE,
        /** RaceHub answered but refused the key. */
        KEY_REFUSED,
        /** RaceHub answered something else unexpected. */
        ERROR
    }

    /**
     * What the director's indicator shows.
     *
     * @param health        see {@link Health}
     * @param detail        what is wrong, or null when {@code OK}
     * @param checkedAt     when the link was last checked, or null
     * @param lastOkAt      when it last worked, or null if it has not since the app started
     * @param waiting       results exports not yet sent
     * @param failed        the part of {@code waiting} whose send failed
     * @param lastSentAt    when RaceHub last accepted results, or null
     * @param lastSendError why the last send failed, or null
     */
    public record Status(Health health, String detail, Instant checkedAt, Instant lastOkAt, int waiting, int failed,
                         Instant lastSentAt, String lastSendError) {
    }

    /** The background check's last answer, tied to the address and key it was made with so a change invalidates it. */
    private record Cached(TestResult result, String address, int keyFingerprint) {
    }

    private static final int MAX_DETAIL = 300;
    /** How a refused key is worded; {@link #healthOf} looks for it, so the two stay together. */
    private static final String REFUSED = "RaceHub refused the key";

    private final RaceHubResultsEndpoint endpoint;
    private final ResultsOutboxQuery outbox;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ModuleSwitch moduleSwitch;
    private final Clock clock;

    private volatile Cached cached;
    private volatile Instant lastOkAt;

    @Autowired
    public RaceHubConnectionService(RaceHubResultsEndpoint endpoint, ResultsOutboxQuery outbox,
                                    ObjectMapper objectMapper, RestClient.Builder restClientBuilder,
                                    ModuleSwitch moduleSwitch) {
        this(endpoint, outbox, objectMapper, restClientBuilder, moduleSwitch, Clock.systemUTC());
    }

    RaceHubConnectionService(RaceHubResultsEndpoint endpoint, ResultsOutboxQuery outbox, ObjectMapper objectMapper,
                             RestClient.Builder restClientBuilder, ModuleSwitch moduleSwitch, Clock clock) {
        this.endpoint = endpoint;
        this.moduleSwitch = moduleSwitch;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.clock = clock;
    }

    /**
     * The full test, for the admin's button: address, key, reach, {@code entries:read} and {@code results:write}.
     * Blocks for up to about 30 seconds in the worst case. Also refreshes the director's indicator, so it turns
     * green as soon as a fixed key passes. Never throws.
     */
    public TestResult test() {
        RaceHubResultsEndpoint.Snapshot settings = endpoint.snapshot();
        String token = endpoint.token();
        TestResult result = run(true, settings, token);
        remember(result, settings, token);
        return result;
    }

    /** The background check, once a minute while RaceHub is configured and the results export module is on. Never throws. */
    @Scheduled(initialDelay = 20_000, fixedDelayString = "${rctiming.racehub.check-interval-ms:60000}")
    public void refresh() {
        try {
            RaceHubResultsEndpoint.Snapshot settings = endpoint.snapshot();
            if (moduleSwitch.isEnabled(ResultsExportModule.ID) && settings.sendingEnabled()) {
                String token = endpoint.token();
                remember(run(false, settings, token), settings, token);
            }
        } catch (RuntimeException e) {
            log.warn("The RaceHub connection check failed unexpectedly", e);
        }
    }

    /** What the director's indicator shows now: the last check (if it matches the settings) and the outbox. */
    public Status status() {
        RaceHubResultsEndpoint.Snapshot settings = endpoint.snapshot();
        if (!settings.sendingEnabled()) {
            return new Status(Health.NOT_CONFIGURED, null, null, null, 0, 0, null, null);
        }
        ResultsOutboxQuery.Summary out = outbox.summary();
        Cached last = cached;
        boolean current = last != null && last.address.equals(String.valueOf(settings.resultsUrl()))
                && last.keyFingerprint == Objects.hashCode(endpoint.token());
        Health health = current ? healthOf(last.result) : Health.CHECKING;
        String detail = current && health != Health.OK ? firstProblem(last.result) : null;
        return new Status(health, detail, current ? last.result.checkedAt : null, lastOkAt, out.waiting(), out.failed(),
                out.lastSentAt(), out.lastError());
    }

    /**
     * Keeps a result, tied to the address and key it was made with (read before the calls, which can take many
     * seconds, so a change made meanwhile leaves this result stale rather than mislabelled). The key is compared by
     * its hash only so the key itself is not held a second time.
     */
    private void remember(TestResult result, RaceHubResultsEndpoint.Snapshot settings, String token) {
        cached = new Cached(result, String.valueOf(settings.resultsUrl()), Objects.hashCode(token));
        if (healthOf(result) == Health.OK) {
            lastOkAt = result.checkedAt;
        }
    }

    private static Health healthOf(TestResult result) {
        Check reach = find(result, "reach");
        Check read = find(result, "read");
        if (reach != null && reach.state == CheckState.FAIL) {
            return Health.UNREACHABLE;
        }
        if (read == null || read.state == CheckState.SKIPPED) {
            return Health.ERROR;
        }
        if (read.state == CheckState.FAIL) {
            return read.detail != null && read.detail.startsWith(REFUSED) ? Health.KEY_REFUSED : Health.ERROR;
        }
        // Only the read decides the indicator, so it reads the same whether the admin's test (which also tries
        // results) or the background check ran last; a slow-down answer (429) still means RaceHub is there
        return Health.OK;
    }

    private static String firstProblem(TestResult result) {
        return result.checks.stream().filter(c -> c.state == CheckState.FAIL || c.state == CheckState.UNKNOWN)
                .map(Check::detail).findFirst().orElse(result.summary);
    }

    private static Check find(TestResult result, String id) {
        return result.checks.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
    }

    private TestResult run(boolean withWriteProbe, RaceHubResultsEndpoint.Snapshot settings, String token) {
        Instant now = clock.instant();
        List<Check> checks = new ArrayList<>();
        URI events = settings.eventsUrl();
        URI results = settings.resultsUrl();

        if (events == null || results == null) {
            checks.add(new Check("address", "RaceHub address", CheckState.FAIL,
                    "No RaceHub address is set. Enter RaceHub's address (such as https://racehub.example) and save it."));
        } else {
            checks.add(new Check("address", "RaceHub address", CheckState.PASS, RaceHubResultsEndpoint.describe(events.toString())));
        }
        if (token == null) {
            checks.add(new Check("key", "RaceHub key", CheckState.FAIL,
                    "No RaceHub key is set. Paste the key RaceHub gave the club and save it."));
        } else {
            checks.add(new Check("key", "RaceHub key", CheckState.PASS, "A key is set."));
        }
        if (events == null || results == null || token == null) {
            checks.add(skipped("reach", "RaceHub reachable"));
            checks.add(skipped("read", "Key can read entries"));
            checks.add(skipped("write", "Key can send results"));
            return new TestResult(false, "RaceHub isn't set up yet.", now, null, checks);
        }

        Integer readyEvents = null;
        Probe read = call(events, token, null);
        if (read.failure != null) {
            checks.add(new Check("reach", "RaceHub reachable", CheckState.FAIL, read.failure));
            checks.add(skipped("read", "Key can read entries"));
            checks.add(skipped("write", "Key can send results"));
            return new TestResult(false, read.failure, now, null, checks);
        }
        checks.add(new Check("reach", "RaceHub reachable", CheckState.PASS, "RaceHub answered."));
        boolean readOk = false;
        switch (read.status) {
            case 200, 304 -> {
                if (read.status == 200 && !read.json) {
                    checks.add(new Check("read", "Key can read entries", CheckState.FAIL,
                            "That address answered, but not like RaceHub (the reply was not data). Check the address; "
                                    + "if RaceHub is behind Cloudflare Access, check the Access settings."));
                } else {
                    readOk = true;
                    readyEvents = read.status == 200 ? countEvents(read.body) : null;
                    checks.add(new Check("read", "Key can read entries", CheckState.PASS, readyEvents == null
                            ? "RaceHub accepted the key." : readyEvents + (readyEvents == 1 ? " meeting" : " meetings")
                                    + " released to timing."));
                }
            }
            case 401 -> checks.add(new Check("read", "Key can read entries", CheckState.FAIL,
                    REFUSED + " (401): it was not recognised, or it has been revoked or has expired. "
                            + "Make a new key in RaceHub and paste it in."));
            case 403 -> checks.add(new Check("read", "Key can read entries", CheckState.FAIL,
                    REFUSED + " (403): it may not have the entries:read ability, or belong to another club. "
                            + "If RaceHub is behind Cloudflare Access, check the service token in the settings file."));
            case 404 -> checks.add(new Check("read", "Key can read entries", CheckState.FAIL,
                    "RaceHub answered 404: this address is not a RaceHub, or the path to it is wrong."));
            case 429 -> checks.add(new Check("read", "Key can read entries", CheckState.UNKNOWN,
                    "RaceHub asked us to slow down (429). Try again in a minute."));
            default -> checks.add(new Check("read", "Key can read entries", CheckState.FAIL,
                    "RaceHub answered " + read.status + ", which was not expected. Try again; if it persists, tell RaceHub's admin."));
        }

        if (!withWriteProbe) {
            checks.add(skipped("write", "Key can send results"));
        } else if (read.status == 401 || read.status == 404) {
            checks.add(skipped("write", "Key can send results"));
        } else {
            Probe write = call(results, token, "{}");
            if (write.failure != null) {
                checks.add(new Check("write", "Key can send results", CheckState.FAIL, write.failure));
            } else {
                switch (write.status) {
                    case 400, 422 -> checks.add(new Check("write", "Key can send results", CheckState.PASS,
                            "RaceHub accepts this key for results. Nothing was stored by the test."));
                    case 200, 201 -> checks.add(new Check("write", "Key can send results", CheckState.PASS,
                            "RaceHub accepts this key for results."));
                    case 401 -> checks.add(new Check("write", "Key can send results", CheckState.FAIL,
                            "RaceHub refused the key (401) when sending results."));
                    case 403 -> checks.add(new Check("write", "Key can send results", CheckState.FAIL,
                            "RaceHub refused the key (403) for results: it needs the results:write ability. "
                                    + "Make a key with both entries:read and results:write."));
                    case 429 -> checks.add(new Check("write", "Key can send results", CheckState.UNKNOWN,
                            "RaceHub asked us to slow down (429). Try again in a minute."));
                    default -> checks.add(new Check("write", "Key can send results", CheckState.FAIL,
                            "RaceHub answered " + write.status + " for results, which was not expected."));
                }
            }
        }

        boolean ok = readOk && checks.stream().allMatch(c -> c.state == CheckState.PASS || (!withWriteProbe && c.id.equals("write")));
        String summary = ok ? "Connected. " + (withWriteProbe ? "RaceHub accepts this key for entries and results."
                : "RaceHub accepts the key.") : firstProblem(new TestResult(false, "RaceHub is not working yet.", now, null, checks));
        return new TestResult(ok, summary, now, readyEvents, checks);
    }

    private static Check skipped(String id, String label) {
        return new Check(id, label, CheckState.SKIPPED, "Not tried.");
    }

    /** One HTTP answer; {@code failure} is set instead when no answer came. */
    private record Probe(int status, boolean json, String body, String failure) {
    }

    private Probe call(URI url, String token, String postBody) {
        try {
            RestClient.RequestHeadersSpec<?> spec = postBody == null
                    ? restClient.get().uri(url)
                    : restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(postBody);
            return spec.accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.setBearerAuth(token);
                        endpoint.accessHeaders(url).forEach(h::set);
                    })
                    .exchange((request, response) -> {
                        MediaType type = response.getHeaders().getContentType();
                        boolean json = type != null && (MediaType.APPLICATION_JSON.isCompatibleWith(type)
                                || type.getSubtype().endsWith("+json"));
                        String body = "";
                        if (response.getStatusCode().value() == 200 && postBody == null) {
                            body = new String(response.getBody().readNBytes(1024 * 1024), StandardCharsets.UTF_8);
                        }
                        return new Probe(response.getStatusCode().value(), json, body, null);
                    });
        } catch (RuntimeException e) {
            return new Probe(0, false, "", describeFailure(e));
        }
    }

    /** A plain sentence for a connection that did not complete; never echoes the address or any header. */
    static String describeFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) {
                return "RaceHub's address could not be found. Check it for typos, and that this laptop is online.";
            }
            if (t instanceof ConnectException) {
                return "Could not connect to RaceHub. Check the address, and that this laptop is online.";
            }
            if (t instanceof SocketTimeoutException) {
                return "RaceHub did not answer in time. Check the internet connection and try again.";
            }
            if (t instanceof SSLException) {
                return "The secure connection to RaceHub failed (certificate problem). Check the address starts with https:// "
                        + "and that this laptop's clock is right.";
            }
            if (t instanceof IOException && t.getMessage() != null && t.getMessage().toLowerCase().contains("timed out")) {
                return "RaceHub did not answer in time. Check the internet connection and try again.";
            }
        }
        String text = "Could not reach RaceHub (" + e.getClass().getSimpleName() + ").";
        return text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) : text;
    }

    /** Counts the meetings in RaceHub's list, whether it is a bare array or holds the list under {@code events} or {@code data}. */
    private Integer countEvents(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode node : new JsonNode[] {root, root.path("events"), root.path("data")}) {
                if (node.isArray()) {
                    return node.size();
                }
            }
        } catch (RuntimeException e) {
            // A list we cannot count still proves the key works
        }
        return null;
    }
}
