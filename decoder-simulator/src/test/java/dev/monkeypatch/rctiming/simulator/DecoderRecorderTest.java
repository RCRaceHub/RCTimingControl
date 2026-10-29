package dev.monkeypatch.rctiming.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecoderRecorderTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    @Test
    void recordsEveryCompleteRecordRawAndStampsTheSidecar(@TempDir Path dir) throws IOException {
        String stream = "\u0001#\t20\t1\t72\t0\txDEAD\r\n\u0001@\t20\t2\t8533156\t1.234\t400\t163\t2\txBEEF\r\n\u0001@\t20\t3\tpartial";
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        ByteArrayOutputStream times = new ByteArrayOutputStream();

        long count = new DecoderRecorder("h", 5100, dir.resolve("x.dump"), clock)
                .record(new ByteArrayInputStream(stream.getBytes(StandardCharsets.US_ASCII)), dump, times);

        assertThat(count).isEqualTo(2);
        assertThat(dump.toString(StandardCharsets.US_ASCII))
                .isEqualTo("\u0001#\t20\t1\t72\t0\txDEAD\r\n\u0001@\t20\t2\t8533156\t1.234\t400\t163\t2\txBEEF\r\n");
        assertThat(times.toString(StandardCharsets.US_ASCII))
                .isEqualTo("1700000000000\t#\t20\t1\t72\t0\txDEAD\n1700000000000\t@\t20\t2\t8533156\t1.234\t400\t163\t2\txBEEF\n");
    }

    @Test
    void theRecordedDumpIsWhatPlaybackModeReplays(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("x.dump");
        String stream = "\u0001#\t20\t1\t72\t0\txDEAD\r\n\u0001@\t20\t2\t8533156\t1.234\t400\t163\t2\txBEEF\r\n";
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        new DecoderRecorder("h", 5100, file, clock).record(
                new ByteArrayInputStream(stream.getBytes(StandardCharsets.US_ASCII)), dump, new ByteArrayOutputStream());
        Files.write(file, dump.toByteArray());

        ByteArrayOutputStream replayed = new ByteArrayOutputStream();
        PlaybackMode.replay(replayed, file.toString(), 1000);

        assertThat(replayed.toString(StandardCharsets.US_ASCII)).isEqualTo(stream);
    }

    @Test
    void aLineOverTheCapIsDroppedAndRecordingContinues(@TempDir Path dir) throws IOException {
        String stream = "x".repeat(20_000) + "\n\u0001#\t20\t1\t72\t0\txDEAD\r\n";
        ByteArrayOutputStream dump = new ByteArrayOutputStream();

        long count = new DecoderRecorder("h", 5100, dir.resolve("x.dump"), clock).record(
                new ByteArrayInputStream(stream.getBytes(StandardCharsets.US_ASCII)), dump, new ByteArrayOutputStream());

        assertThat(count).isEqualTo(1);
        assertThat(dump.toString(StandardCharsets.US_ASCII)).isEqualTo("\u0001#\t20\t1\t72\t0\txDEAD\r\n");
    }

    @Test
    void aWriteFailureIsNotMistakenForALostConnection(@TempDir Path dir) {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("No space left on device");
            }
        };

        assertThatThrownBy(() -> new DecoderRecorder("h", 5100, dir.resolve("x.dump"), clock)
                .record(new ByteArrayInputStream("a\n".getBytes(StandardCharsets.US_ASCII)), broken, broken))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void stopEndsARunBlockedOnASilentDecoder(@TempDir Path dir) throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            DecoderRecorder recorder = new DecoderRecorder("127.0.0.1", silent.getLocalPort(), dir.resolve("x.dump"));
            CompletableFuture<Void> running = CompletableFuture.runAsync(() -> {
                try {
                    recorder.run();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try (Socket accepted = silent.accept()) {
                recorder.stop();

                running.get(5, TimeUnit.SECONDS);
                assertThat(running).isCompleted();
            }
        }
    }

    @Test
    void everyConnectedClientReceivesEachRecord(@TempDir Path dir) throws Exception {
        try (RecordFanOut fanOut = new RecordFanOut(0);
             Socket one = new Socket("127.0.0.1", fanOut.port());
             Socket two = new Socket("127.0.0.1", fanOut.port())) {
            one.setSoTimeout(5_000);
            two.setSoTimeout(5_000);
            String stream = "\u0001#\t20\t1\t72\t0\txDEAD\r\n";
            DecoderRecorder recorder = new DecoderRecorder("h", 5100, dir.resolve("x.dump"), clock, fanOut);
            // the acceptor registers clients on its own thread
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fanOut.clientCount() < 2 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
            byte[] expected = stream.getBytes(StandardCharsets.US_ASCII);
            recorder.record(new ByteArrayInputStream(expected), new ByteArrayOutputStream(), new ByteArrayOutputStream());

            assertThat(one.getInputStream().readNBytes(expected.length)).isEqualTo(expected);
            assertThat(two.getInputStream().readNBytes(expected.length)).isEqualTo(expected);
        }
    }
}
