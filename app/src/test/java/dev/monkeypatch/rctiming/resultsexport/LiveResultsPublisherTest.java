package dev.monkeypatch.rctiming.resultsexport;

import com.sun.net.httpserver.HttpServer;
import dev.monkeypatch.rctiming.TestJson;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.livefeed.LiveFeedRaceLookup;
import dev.monkeypatch.rctiming.livefeed.LiveFeedV1;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The live results publisher (#815 follow-up): throttled, provisional, and never blocked by a RaceHub that is down. */
class LiveResultsPublisherTest {

    private final LiveFeedRaceLookup lookup = mock(LiveFeedRaceLookup.class);
    private final LapTimingService timing = mock(LapTimingService.class);
    private final RaceClockService clocks = mock(RaceClockService.class);
    private final ResultsPublishSettings publishSettings = mock(ResultsPublishSettings.class);
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(202);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-26T14:00:00Z"));
    private HttpServer raceHub;
    private LiveResultsPublisher publisher;
    private String raceStatus = "RUNNING";
    private String racehubEventId = "evt-1";

    @BeforeEach
    void setUp() throws IOException {
        raceHub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        raceHub.createContext("/api/rctc/v1/live", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        raceHub.start();
        var properties = new RaceHubResultsProperties(null, "club-token", null, null,
                URI.create("http://127.0.0.1:" + raceHub.getAddress().getPort()));
        var endpoint = new RaceHubResultsEndpoint(properties, mock(ClubProfileService.class));
        when(publishSettings.effective()).thenReturn(ResultsPublishTarget.RACEHUB);
        when(lookup.find(anyLong())).thenAnswer(inv -> Optional.of(race()));
        when(clocks.clockOf(anyLong(), any(), any())).thenReturn(new RaceClockDto(7, "RUNNING", 1000, 300000L, 299000L, true));
        when(timing.peek(anyLong())).thenReturn(Optional.empty());
        publisher = new LiveResultsPublisher(new LiveResultsProperties(true, 3000, 15000), endpoint, publishSettings,
                lookup, timing, clocks, TestJson.mapper(), RestClient.builder(), clock);
    }

    @AfterEach
    void tearDown() {
        raceHub.stop(0);
    }

    @Test
    void sendsAProvisionalSnapshotOfARunningRace() {
        follow();
        publisher.tick();

        assertThat(bodies).hasSize(1);
        assertThat(bodies.get(0)).contains("\"type\":\"live_results\"", "\"provisional\":true",
                "\"racehub_event_id\":\"evt-1\"", "\"status\":\"RUNNING\"");
    }

    @Test
    void anUnchangedRaceIsNotSentAgainUntilTheResendTime() {
        follow();
        publisher.tick();
        clock.advance(Duration.ofSeconds(5));
        publisher.tick();
        assertThat(bodies).hasSize(1);

        clock.advance(Duration.ofSeconds(11));
        publisher.tick();
        assertThat(bodies).hasSize(2);
    }

    @Test
    void aChangeIsHeldBackUntilTheMinimumIntervalHasPassed() {
        follow();
        publisher.tick();
        raceStatus = "STOPPED";
        clock.advance(Duration.ofSeconds(1));
        publisher.tick();
        assertThat(bodies).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        publisher.tick();
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(1)).contains("\"status\":\"STOPPED\"");
    }

    @Test
    void aRaceHubThatIsDownIsLeftAloneUntilTheBackoffEnds() {
        status.set(500);
        follow();
        publisher.tick();
        clock.advance(Duration.ofSeconds(2));
        publisher.tick();
        assertThat(bodies).hasSize(1);

        status.set(202);
        clock.advance(Duration.ofSeconds(4));
        publisher.tick();
        assertThat(bodies).hasSize(2);
    }

    @Test
    void aFinishedRaceSendsOneLastSnapshotAndIsThenLeftAlone() {
        follow();
        publisher.tick();
        raceStatus = "FINISHED";
        publisher.onRaceStatusChanged(new RaceStatusChangedEvent(this, 7L, RaceStatus.FINISHED));
        publisher.tick();
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(1)).contains("\"status\":\"FINISHED\"");

        clock.advance(Duration.ofSeconds(30));
        publisher.tick();
        assertThat(bodies).hasSize(2);
    }

    @Test
    void anEventNotFromRaceHubIsNeverSent() {
        racehubEventId = null;
        follow();
        publisher.tick();
        assertThat(bodies).isEmpty();
    }

    @Test
    void nothingIsSentWhenTheClubDoesNotPublishToRaceHub() {
        when(publishSettings.effective()).thenReturn(ResultsPublishTarget.NONE);
        follow();
        publisher.tick();
        assertThat(bodies).isEmpty();
    }

    private void follow() {
        publisher.onRaceStatusChanged(new RaceStatusChangedEvent(
                this, 7L, RaceStatus.RUNNING));
    }

    private LiveFeedRaceLookup.RaceInfo race() {
        return new LiveFeedRaceLookup.RaceInfo(7, raceStatus, false, racehubEventId,
                new LiveFeedV1.Event(1, "Club day", "2026-11-26"), "2WD", "QUALIFIER", 1, 1, null, 300000L,
                List.of(new LiveFeedRaceLookup.GridCar(100, "Alex", 5)));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

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
