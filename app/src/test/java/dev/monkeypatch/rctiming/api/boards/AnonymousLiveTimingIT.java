package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L12 acceptance: a spectator board with no login connects to the STOMP endpoint and follows a
 * simulated race on its timing and state topics, and can't reach any other topic.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class AnonymousLiveTimingIT extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired CompetitorService competitorService;
    @Autowired LapTimingService lapTimingService;
    @Autowired LiveTimingHub liveTimingHub;
    @Autowired SimpMessagingTemplate messagingTemplate;

    private BoardFixtures fx;
    private WebSocketStompClient stompClient;
    private StompSession session;
    private Race race;

    @BeforeEach
    void setUp() {
        fx = new BoardFixtures(eventRepository, racingClassRepository, eventClassRepository,
                roundRepository, raceRepository, entryRepository, raceEntryRepository, competitorService);
        race = fx.race(fx.round(RoundType.QUALIFIER, 1, 1), 1, RaceStatus.RUNNING);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
        lapTimingService.releaseState(race.getId());
        fx.cleanUp();
    }

    @Test
    void anonymousSpectatorFollowsASimulatedRaceWithoutLogin() throws Exception {
        String transponder = "S" + fx.suffix;
        String driver = "Spectated Racer " + fx.suffix;
        fx.gridEntry(race, driver, transponder, 1);
        session = connect(new StompHeaders());

        BlockingQueue<Object> timing = subscribe("/topic/race/" + race.getId() + "/timing", List.class);
        BlockingQueue<Object> state = subscribe("/topic/race/" + race.getId() + "/state", Map.class);

        // Keep the simulated decoder passing until the subscription is live and a frame lands
        List<Map> rows = null;
        for (int lap = 0; lap < 25 && rows == null; lap++) {
            lapTimingService.onLapPassing(new LapPassingEvent(race.getId(), transponder, (lap + 1) * 20_000_000L));
            rows = (List<Map>) timing.poll(200, TimeUnit.MILLISECONDS);
        }
        assertThat(rows).as("live timing frame for an anonymous subscriber").isNotNull();
        assertThat(rows).extracting(r -> r.get("driverName")).contains(driver);

        // The broker may register SUBSCRIBEs out of order, so the state subscription can still be
        // settling after the first timing frame lands: keep broadcasting until one arrives
        Map change = null;
        for (int i = 0; i < 25 && change == null; i++) {
            liveTimingHub.broadcastStateChange(race.getId(), RaceStatus.STOPPED);
            change = (Map) state.poll(200, TimeUnit.MILLISECONDS);
        }
        assertThat(change).as("race state frame for an anonymous subscriber").isNotNull();
        assertThat(change.get("newStatus")).isEqualTo("STOPPED");
    }

    @Test
    void anonymousSpectatorCannotSubscribeToOfficialsTopics() throws Exception {
        session = connect(new StompHeaders());

        BlockingQueue<Object> marshal = subscribe("/topic/race/" + race.getId() + "/marshal", Map.class);
        BlockingQueue<Object> decoder = subscribe("/topic/system/decoder-status", Map.class);
        BlockingQueue<Object> timing = subscribe("/topic/race/" + race.getId() + "/timing", List.class);

        // The allowed subscription, made last on the same session, proves the others were processed
        Object frame = null;
        for (int i = 0; i < 25 && frame == null; i++) {
            liveTimingHub.broadcastTimingUpdate(race.getId(), List.of());
            frame = timing.poll(200, TimeUnit.MILLISECONDS);
        }
        assertThat(frame).isNotNull();

        messagingTemplate.convertAndSend("/topic/race/" + race.getId() + "/marshal", (Object) Map.of("lapDelta", 1));
        messagingTemplate.convertAndSend("/topic/system/decoder-status", (Object) Map.of("decoderState", "CONNECTED"));
        assertThat(marshal.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(decoder.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void invalidTokenIsStillRejected() {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer not-a-real-token");

        assertThatThrownBy(() -> connect(headers)).isInstanceOf(Exception.class);
    }

    private StompSession connect(StompHeaders connectHeaders) throws Exception {
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws/timing", new WebSocketHttpHeaders(),
                        connectHeaders, new StompSessionHandlerAdapter() {})
                .get(3, TimeUnit.SECONDS);
    }

    private BlockingQueue<Object> subscribe(String topic, Class<?> payloadType) {
        BlockingQueue<Object> frames = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(payload);
            }
        });
        return frames;
    }
}
