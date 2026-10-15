package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L1 acceptance: the app reads RC-4 records straight from the decoder's TCP port, with no
 * A fake decoder (a plain {@link ServerSocket}) stands in for the hardware.
 *
 * <p>Covers the three acceptance points: laps reach live timing for a RUNNING race, the decoder
 * status moves between connected, reconnecting and disconnected, and changing the club's decoder
 * address reconnects without a restart.
 */
class DecoderListenerIT extends AbstractIntegrationTest {

    private static final long RACE_ID = 4242L;
    private static final long WAIT_MS = TimeUnit.SECONDS.toMillis(10);

    @MockitoBean
    RaceRepository raceRepository;

    @Autowired
    ClubProfileService clubProfileService;

    @Autowired
    ClubProfileRepository clubProfileRepository;

    @Autowired
    DecoderStatusPublisher statusPublisher;

    @Autowired
    LapTimingService lapTimingService;

    @BeforeEach
    void setUp() {
        clubProfileRepository.deleteAll();
        clubProfileRepository.save(minimalClub());

        Race runningRace = mock(Race.class);
        when(runningRace.getId()).thenReturn(RACE_ID);
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.of(runningRace));
        // The state is released after each test, as a finish does; starting the race takes passings again
        lapTimingService.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE_ID, RaceStatus.RUNNING));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clearing the config stops the listener's socket, so the next test starts from DISCONNECTED.
        clubProfileService.updateDecoderConfig(Actor.system("test"), null, null, null);
        awaitStatus("DISCONNECTED");
        clubProfileRepository.deleteAll();
        lapTimingService.releaseState(RACE_ID);
    }

    @Test
    void lapsFromDecoderSocketReachLiveTimingForRunningRace() throws Exception {
        try (FakeDecoder decoder = new FakeDecoder()) {
            clubProfileService.updateDecoderConfig(Actor.system("test"), "localhost", decoder.port(), "RC4");
            decoder.acceptClient();
            awaitStatus("CONNECTED");

            decoder.send(
                    passingLine(1, "12345", "10.000"),
                    passingLine(2, "12345", "12.500"));

            awaitUntil("two laps recorded for race " + RACE_ID, () ->
                    lapTimingService.peek(RACE_ID)
                            .map(state -> state.getLapHistory().size())
                            .orElse(0) >= 2);

            List<LapPassingEvent> laps = lapTimingService.peek(RACE_ID).orElseThrow().getLapHistory();
            assertThat(laps).extracting(LapPassingEvent::transponderNumber).containsOnly("12345");
            assertThat(laps).extracting(LapPassingEvent::raceId).containsOnly(RACE_ID);
        }
    }

    @Test
    void changingDecoderAddressReconnectsWithoutRestart() throws Exception {
        try (FakeDecoder first = new FakeDecoder(); FakeDecoder second = new FakeDecoder()) {
            clubProfileService.updateDecoderConfig(Actor.system("test"), "localhost", first.port(), "RC4");
            first.acceptClient();
            awaitStatus("CONNECTED");

            clubProfileService.updateDecoderConfig(Actor.system("test"), "localhost", second.port(), "RC4");
            second.acceptClient();
            awaitStatus("CONNECTED");
        }
    }

    @Test
    void decoderDropReportsReconnectingAndClearingConfigReportsDisconnected() throws Exception {
        FakeDecoder decoder = new FakeDecoder();
        try {
            clubProfileService.updateDecoderConfig(Actor.system("test"), "localhost", decoder.port(), "RC4");
            decoder.acceptClient();
            awaitStatus("CONNECTED");

            decoder.close();
            awaitStatus("RECONNECTING");

            clubProfileService.updateDecoderConfig(Actor.system("test"), null, null, null);
            awaitStatus("DISCONNECTED");
        } finally {
            decoder.close();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** RC-4 PASSING record with the SOH prefix and CRLF terminator the decoder sends. */
    private static String passingLine(int seq, String transponder, String timeSinceStartSeconds) {
        return "\u0001@\t20\t" + seq + "\t" + transponder + "\t" + timeSinceStartSeconds
                + "\t400\t163\t2\txDEAD\r\n";
    }

    private void awaitStatus(String expected) throws InterruptedException {
        awaitUntil("decoder status " + expected, () ->
                expected.equals(statusPublisher.getLastKnownStatus().decoderState()));
    }

    private static void awaitUntil(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for " + description);
            }
            Thread.sleep(50);
        }
    }

    private static ClubProfile minimalClub() {
        Instant now = Instant.now();
        ClubProfile profile = new ClubProfile();
        profile.setName("Test Club");
        profile.setTimezone("UTC");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profile;
    }

    /** Minimal stand-in for the AMB decoder: accepts one client and writes lines to it. */
    private static final class FakeDecoder implements AutoCloseable {

        private final ServerSocket server;
        private Socket client;

        FakeDecoder() throws IOException {
            server = new ServerSocket(0);
            server.setSoTimeout((int) WAIT_MS);
        }

        int port() {
            return server.getLocalPort();
        }

        void acceptClient() throws IOException {
            client = server.accept();
        }

        void send(String... lines) throws IOException {
            OutputStream out = client.getOutputStream();
            for (String line : lines) {
                out.write(line.getBytes(StandardCharsets.US_ASCII));
            }
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (client != null) {
                client.close();
            }
            server.close();
        }
    }
}
