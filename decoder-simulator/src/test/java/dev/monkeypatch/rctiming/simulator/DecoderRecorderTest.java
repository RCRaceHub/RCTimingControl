package dev.monkeypatch.rctiming.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

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
    void theDumpIsWhatPlaybackModeReads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("x.dump");
        String record = "@\t20\t2\t8533156\t1.234\t400\t163\t2\txBEEF";
        ByteArrayOutputStream dump = new ByteArrayOutputStream();
        Rc4Lines.write(dump, record);
        Files.write(file, dump.toByteArray());

        ByteArrayOutputStream replayed = new ByteArrayOutputStream();
        PlaybackMode.replay(replayed, file.toString(), 1000);

        assertThat(replayed.toByteArray()).isEqualTo(dump.toByteArray());
    }
}
