package dev.monkeypatch.rctiming.livefeed;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.simulator.relay.LiveFeedTestRelay;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live feed (#28) end to end through the test relay: a viewer sees the race live, only display names go
 * out, a missing relay never holds timing up, and the feed resumes in full when the relay comes back.
 */
class LiveFeedIT extends AbstractIntegrationTest {

    private static final String RELAY_KEY = "relay-key";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LiveFeedRaceLookup raceLookup;
    @Autowired LapTimingService lapTimingService;
    @Autowired LiveTimingHub liveTimingHub;
    @Autowired RaceRepository raceRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired RaceFormatService raceFormatService;

    private final HttpClient client = HttpClient.newHttpClient();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T14:00:00Z"));
    private String run;
    private long eventId;
    private long raceId;
    private LiveFeedTestRelay relay;
    private LiveFeedPublisher publisher;
    private RaceClockService raceClocks;

    @BeforeEach
    void setUp() throws InterruptedException {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status, live_feed_enabled)
                values (?, '2026-10-18', 'IN_PROGRESS', 1) returning id""", Long.class, "Live feed " + run);
        long racingClassId = jdbc.queryForObject("insert into racing_classes (name) values (?) returning id",
                Long.class, "Feed Buggy " + run);
        long eventClassId = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED","durationMinutes":5}') returning id""",
                Long.class, eventId, racingClassId);
        long roundId = jdbc.queryForObject("""
                insert into rounds (event_id, type, round_number, sequence_in_event)
                values (?, 'FINAL', 1, 1) returning id""", Long.class, eventId);
        raceId = jdbc.queryForObject("""
                insert into races (round_id, event_class_id, heat_number, sequence_in_round, final_letter,
                                   start_type, status)
                values (?, ?, 1, 1, 'A', 'GRID', 'RUNNING') returning id""", Long.class, roundId, eventClassId);
        long ada = entry(eventClassId, "Ada Lovelace", "1" + run, 7);
        long grace = entry(eventClassId, "Grace Hopper", "2" + run, null);

        // Ada leads by a lap's worth of a second; Grace is a lap down
        LiveRaceState state = lapTimingService.stateFor(raceId);
        long start = clock.instant().toEpochMilli() * 1000;
        // The first crossing of the line only starts timing the first lap
        passing(state, ada, "1" + run, start);
        passing(state, grace, "2" + run, start + 1_000_000L);
        passing(state, ada, "1" + run, start + 20_000_000L);
        passing(state, grace, "2" + run, start + 21_000_000L);
        passing(state, ada, "1" + run, start + 40_000_000L);

        raceClocks = new RaceClockService(raceRepository, roundRepository, eventClassRepository, raceFormatService,
                clock);
        relay = new LiveFeedTestRelay(0, RELAY_KEY);
        int port = relay.start();
        publisher = publisher(new LiveFeedProperties(URI.create("ws://127.0.0.1:" + port + "/publish"), RELAY_KEY));
        status(publisher, RaceStatus.GRID);
        status(publisher, RaceStatus.RUNNING);
    }

    @AfterEach
    void tearDown() {
        publisher.stop();
        if (relay != null) {
            relay.close();
        }
    }

    @Test
    void aViewerSeesTheRaceLiveWithDisplayNamesOnly() throws Exception {
        List<String> seen = watch(relay.port());
        clock.advance(Duration.ofSeconds(65));

        publisher.tick();

        assertThat(publisher.state()).isEqualTo(LiveFeedState.CONNECTED);
        await(() -> !seen.isEmpty());
        JsonNode message = objectMapper.readTree(seen.get(0));
        assertThat(LiveFeedV1GoldenTest.validate(message)).isEmpty();
        assertThat(message.at("/event/name").asString()).isEqualTo("Live feed " + run);
        assertThat(message.at("/race/rctc_race_id").asLong()).isEqualTo(raceId);
        assertThat(message.at("/race/class_name").asString()).isEqualTo("Feed Buggy " + run);
        assertThat(message.at("/race/status").asString()).isEqualTo("RUNNING");
        assertThat(message.at("/race/clock/elapsed_ms").asLong()).isEqualTo(65_000);
        assertThat(message.at("/race/clock/duration_ms").asLong()).isEqualTo(300_000);
        assertThat(message.at("/race/clock/remaining_ms").asLong()).isEqualTo(235_000);
        assertThat(message.at("/race/clock/running").asBoolean()).isTrue();
        JsonNode first = message.at("/standings/0");
        assertThat(first.at("/display_name").asString()).isEqualTo("Ada Lovelace");
        assertThat(first.at("/car_number").asInt()).isEqualTo(7);
        assertThat(first.at("/laps").asInt()).isEqualTo(2);
        assertThat(first.at("/last_lap_ms").asLong()).isEqualTo(20_000);
        JsonNode second = message.at("/standings/1");
        assertThat(second.at("/display_name").asString()).isEqualTo("Grace Hopper");
        assertThat(second.at("/car_number").isNull()).isTrue();
        assertThat(second.at("/laps_down").asInt()).isEqualTo(1);
        // No transponder numbers or ids about people
        assertThat(seen.get(0)).doesNotContain("1" + run).doesNotContain("2" + run).doesNotContain("entry_id");
    }

    @Test
    void aRaceIsSentAgainWhenItChangesAndEveryFewSecondsOtherwise() {
        publisher.tick();
        await(() -> relay.receivedCount() == 1);

        // Nothing changed and it was just sent
        clock.advance(Duration.ofSeconds(1));
        publisher.tick();
        assertThat(relay.receivedCount()).isEqualTo(1);

        // A lap is a change
        LiveRaceState state = lapTimingService.stateFor(raceId);
        long ada = state.calculatePositions().get(0).entryId();
        passing(state, ada, "1" + run, clock.instant().toEpochMilli() * 1000 + 60_000_000L);
        clock.advance(Duration.ofSeconds(1));
        publisher.tick();
        await(() -> relay.receivedCount() == 2);

        // And it goes out again after a few quiet seconds, for viewers joining late
        clock.advance(LiveFeedPublisher.RESEND_EVERY);
        publisher.tick();
        await(() -> relay.receivedCount() == 3);
    }

    @Test
    void carsThatHaveNotCrossedTheLineFollowInGridOrder() throws Exception {
        long eventClassId = jdbc.queryForObject("select event_class_id from races where id = ?", Long.class, raceId);
        entry(eventClassId, "Walk-in Wendy", "3" + run, 9);
        List<String> seen = watch(relay.port());

        publisher.tick();

        await(() -> !seen.isEmpty());
        JsonNode message = objectMapper.readTree(seen.get(0));
        assertThat(LiveFeedV1GoldenTest.validate(message)).isEmpty();
        JsonNode third = message.at("/standings/2");
        assertThat(third.at("/position").asInt()).isEqualTo(3);
        assertThat(third.at("/display_name").asString()).isEqualTo("Walk-in Wendy");
        assertThat(third.at("/car_number").asInt()).isEqualTo(9);
        assertThat(third.at("/laps").asInt()).isZero();
        assertThat(third.at("/laps_down").asInt()).isEqualTo(2);
        assertThat(third.at("/last_lap_ms").isNull()).isTrue();
    }

    @Test
    void sequenceKeepsGoingUpWhenTheAppRestarts() throws Exception {
        List<String> seen = watch(relay.port());
        publisher.tick();
        await(() -> seen.size() == 1);
        publisher.stop();

        // A fresh publisher, as after the app restarts a moment later
        clock.advance(Duration.ofSeconds(1));
        LiveFeedPublisher restarted = publisher(new LiveFeedProperties(
                URI.create("ws://127.0.0.1:" + relay.port() + "/publish"), RELAY_KEY));
        try {
            status(restarted, RaceStatus.RUNNING);
            restarted.tick();
            await(() -> seen.size() == 2);
        } finally {
            restarted.stop();
        }

        assertThat(objectMapper.readTree(seen.get(1)).at("/sequence").asLong())
                .isGreaterThan(objectMapper.readTree(seen.get(0)).at("/sequence").asLong());
    }

    @Test
    void nothingIsSentForAnEventWithTheFeedOff() {
        jdbc.update("update events set live_feed_enabled = 0 where id = ?", eventId);

        publisher.tick();

        assertThat(publisher.state()).isEqualTo(LiveFeedState.IDLE);
        assertThat(relay.receivedCount()).isZero();
        assertThat(relay.publisherCount()).isZero();
    }

    @Test
    void aMissingRelayNeverHoldsTimingUpAndTheFeedResumesWhenItReturns() throws Exception {
        publisher.tick();
        assertThat(publisher.state()).isEqualTo(LiveFeedState.CONNECTED);
        int port = relay.port();

        relay.close();
        relay = null;
        // Each pass returns at once, and timing carries on, until the feed notices the relay has gone
        LiveRaceState state = lapTimingService.stateFor(raceId);
        long grace = state.calculatePositions().get(1).entryId();
        for (int i = 0; i < 40 && publisher.state() != LiveFeedState.RECONNECTING; i++) {
            clock.advance(LiveFeedPublisher.RESEND_EVERY);
            long before = System.nanoTime();
            publisher.tick();
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(2));
            passing(state, grace, "2" + run, clock.instant().toEpochMilli() * 1000);
            Thread.sleep(50);
        }
        assertThat(publisher.state()).isEqualTo(LiveFeedState.RECONNECTING);
        assertThat(state.calculatePositions()).hasSize(2);

        relay = new LiveFeedTestRelay(port, RELAY_KEY);
        relay.start();
        List<String> seen = watch(port);
        clock.advance(LiveFeedPublisher.MAX_RETRY);
        publisher.tick();

        assertThat(publisher.state()).isEqualTo(LiveFeedState.CONNECTED);
        await(() -> !seen.isEmpty());
        // The new relay gets the race in full straight away, with the laps run while it was down
        JsonNode message = objectMapper.readTree(seen.get(0));
        assertThat(message.at("/race/rctc_race_id").asLong()).isEqualTo(raceId);
        JsonNode graceRow = null;
        for (JsonNode row : message.at("/standings")) {
            if (row.at("/display_name").asString().equals("Grace Hopper")) {
                graceRow = row;
            }
        }
        assertThat(graceRow).isNotNull();
        assertThat(graceRow.at("/laps").asInt()).isGreaterThan(1);
    }

    @Test
    void aFinishedRaceIsSentOnceMoreThenDropped() throws Exception {
        List<String> seen = watch(relay.port());
        clock.advance(Duration.ofSeconds(30));
        publisher.tick();
        await(() -> seen.size() == 1);

        clock.advance(Duration.ofSeconds(270));
        jdbc.update("update races set status = 'FINISHED' where id = ?", raceId);
        status(publisher, RaceStatus.FINISHED);
        // As finishing does straight after the event: the result is stored and the live state let go
        lapTimingService.releaseState(raceId);
        publisher.tick();

        await(() -> seen.size() == 2);
        JsonNode last = objectMapper.readTree(seen.get(1));
        assertThat(last.at("/race/status").asString()).isEqualTo("FINISHED");
        assertThat(last.at("/race/clock/elapsed_ms").asLong()).isEqualTo(300_000);
        assertThat(last.at("/race/clock/running").asBoolean()).isFalse();
        // The final running order survives the live state being let go
        assertThat(last.at("/standings/0/display_name").asString()).isEqualTo("Ada Lovelace");
        assertThat(last.at("/standings/0/laps").asInt()).isEqualTo(2);
        assertThat(last.at("/standings/1/display_name").asString()).isEqualTo("Grace Hopper");
        assertThat(last.at("/sequence").asLong()).isGreaterThan(objectMapper.readTree(seen.get(0)).at("/sequence").asLong());

        clock.advance(LiveFeedPublisher.RESEND_EVERY);
        publisher.tick();
        assertThat(relay.receivedCount()).isEqualTo(2);

        // With nothing left to send, the connection is closed after a while
        clock.advance(LiveFeedPublisher.IDLE_CLOSE_AFTER.plusSeconds(1));
        publisher.tick();
        assertThat(publisher.state()).isEqualTo(LiveFeedState.IDLE);
    }

    @Test
    void withoutARelaySetUpTheFeedSaysWhatIsMissing() {
        LiveFeedPublisher notSetUp = publisher(new LiveFeedProperties(null, null));
        status(notSetUp, RaceStatus.RUNNING);

        notSetUp.tick();

        assertThat(notSetUp.state()).isEqualTo(LiveFeedState.NOT_SET_UP);
        assertThat(notSetUp.status().missingSettings())
                .containsExactly(LiveFeedProperties.URL_SETTING, LiveFeedProperties.TOKEN_SETTING);
        assertThat(relay.publisherCount()).isZero();
    }

    @Test
    void closingTheConnectionSendsTheRelayACloseFrame() throws Exception {
        LiveFeedConnection connection = new LiveFeedConnection();
        connection.open(URI.create("ws://127.0.0.1:" + relay.port() + "/publish"), RELAY_KEY);
        int before = relay.closeFrameCount();

        connection.close();

        await(() -> relay.closeFrameCount() > before);
    }

    @Test
    void raceDirectorsTurnTheFeedOnAndOffForAnEvent() {
        HttpHeaders director = headers(Set.of(Role.RACE_DIRECTOR));
        HttpHeaders referee = headers(Set.of(Role.REFEREE));
        String setting = "/api/v1/race-control/events/" + eventId + "/live-feed";

        ResponseEntity<JsonNode> off = restTemplate.exchange(setting, HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", false), director), JsonNode.class);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(off.getBody().get("enabled").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select live_feed_enabled from events where id = ?", Boolean.class, eventId))
                .isFalse();

        ResponseEntity<JsonNode> read = restTemplate.exchange(setting, HttpMethod.GET, new HttpEntity<>(referee),
                JsonNode.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("enabled").asBoolean()).isFalse();

        ResponseEntity<String> refused = restTemplate.exchange(setting, HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", true), referee), String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> status = restTemplate.exchange("/api/v1/race-control/live-feed/status",
                HttpMethod.GET, new HttpEntity<>(referee), JsonNode.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("state").asString()).isEqualTo("NOT_SET_UP");

        ResponseEntity<String> anonymous = restTemplate.exchange("/api/v1/race-control/live-feed/status",
                HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(anonymous.getStatusCode().value()).isIn(401, 403);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private LiveFeedPublisher publisher(LiveFeedProperties properties) {
        return new LiveFeedPublisher(properties, raceLookup, lapTimingService, raceClocks, liveTimingHub,
                objectMapper, clock, new LiveFeedConnection());
    }

    /** A race status change, as the app's beans all receive it. */
    private void status(LiveFeedPublisher target, RaceStatus newStatus) {
        RaceStatusChangedEvent event = new RaceStatusChangedEvent(this, raceId, newStatus);
        raceClocks.onRaceStatusChanged(event);
        target.onRaceStatusChanged(event);
    }

    private long entry(long eventClassId, String name, String transponder, Integer carNumber) {
        long competitorId = jdbc.queryForObject("insert into competitors (display_name) values (?) returning id",
                Long.class, name);
        long entryId = jdbc.queryForObject("""
                insert into entries (event_id, event_class_id, competitor_id, transponder_number, status)
                values (?, ?, ?, ?, 'CONFIRMED') returning id""",
                Long.class, eventId, eventClassId, competitorId, transponder);
        jdbc.update("insert into race_entries (race_id, entry_id, car_number) values (?, ?, ?)",
                raceId, entryId, carNumber);
        return entryId;
    }

    private void passing(LiveRaceState state, long entryId, String transponder, long atMicros) {
        state.applyLapPassing(new LapPassingEvent(raceId, transponder, atMicros), entryId);
    }

    /** Connects a viewer to the relay and collects what it is sent. */
    private List<String> watch(int port) throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/watch"), new WebSocket.Listener() {
                    private final StringBuilder text = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            seen.add(text.toString());
                            text.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);
        return seen;
    }

    private static void await(BooleanSupplier check) {
        Awaitility.await("the relay").atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20))
                .until(check::getAsBoolean);
    }

    private HttpHeaders headers(Set<Role> roles) {
        String email = "feed-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"),
                AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(login.getBody().accessToken());
        return headers;
    }

    /** A clock the test moves on by hand. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
