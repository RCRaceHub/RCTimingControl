package dev.monkeypatch.rctiming.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DumpSummaryTest {

    private static String passing(int seq, String transponder, String time) {
        return "\u0001@\t20\t" + seq + "\t" + transponder + "\t" + time + "\t400\t163\t2\txBEEF\r\n";
    }

    private static String status(int seq) {
        return "\u0001#\t20\t" + seq + "\t72\t0\txDEAD\r\n";
    }

    private DumpSummary summarise(Path dir, String content) throws IOException {
        Path file = dir.resolve("x.dump");
        Files.writeString(file, content, StandardCharsets.ISO_8859_1);
        return DumpSummary.read(file);
    }

    @Test
    void countsPassingsPerTransponderAndFindsNoGapsInAnUnbrokenSequence(@TempDir Path dir) throws IOException {
        DumpSummary summary = summarise(dir, status(1) + passing(2, "100", "1.000") + passing(3, "200", "2.000")
                + passing(4, "100", "13.500"));

        assertThat(summary.passings()).isEqualTo(3);
        assertThat(summary.passings("100")).isEqualTo(2);
        assertThat(summary.passings("200")).isEqualTo(1);
        assertThat(summary.gaps()).isEmpty();
    }

    @Test
    void reportsMissingSequenceNumbersAndASequenceThatWentBack(@TempDir Path dir) throws IOException {
        DumpSummary summary = summarise(dir, passing(1, "100", "1.0") + passing(4, "100", "9.0") + status(2));

        assertThat(summary.gaps()).hasSize(2);
        assertThat(summary.gaps().get(0)).isEqualTo("2 missing after 1");
        assertThat(summary.gaps().get(1)).startsWith("sequence went back from 4 to 2");
    }

    @Test
    void countsLinesItCannotReadWithoutStopping(@TempDir Path dir) throws IOException {
        DumpSummary summary = summarise(dir, "junk\r\n" + passing(1, "100", "1.0") + "\u0001@\t20\tx\r\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        summary.print(new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(summary.passings()).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("1 passings, 0 status, 2 unreadable");
    }

    @Test
    void reportsShortestAndLongestTimeBetweenPassings(@TempDir Path dir) throws IOException {
        DumpSummary summary = summarise(dir, passing(1, "100", "10.000") + passing(2, "100", "20.500")
                + passing(3, "100", "25.000"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        summary.print(new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8)).containsPattern("100\\s+3\\s+4\\.500\\s+10\\.500");
    }

    @Test
    void splitsBySilenceAndByRaceWindowUsingTheTimesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("x.dump");
        Files.writeString(file, "");
        Files.writeString(dir.resolve("x.dump.times"),
                "1000\t@\t20\t1\t100\t1.0\t400\t163\t2\txBEEF\n"
                + "2000\t@\t20\t2\t100\t2.0\t400\t163\t2\txBEEF\n"
                + "600000\t@\t20\t3\t200\t9.0\t400\t163\t2\txBEEF\n");

        DumpSummary byGap = DumpSummary.read(file, 120, java.util.List.of());
        DumpSummary byWindow = DumpSummary.read(file, 0, java.util.List.of(
                new DumpSummary.Window("Heat 1", java.time.Instant.ofEpochMilli(0), java.time.Instant.ofEpochMilli(5000)),
                new DumpSummary.Window("Heat 2", java.time.Instant.ofEpochMilli(500000), java.time.Instant.ofEpochMilli(700000))));

        assertThat(byGap.allSections()).extracting(s -> s.passings).containsExactly(2L, 1L);
        assertThat(byWindow.allSections()).extracting(s -> s.name).containsExactly("Heat 1", "Heat 2");
        assertThat(byWindow.allSections()).extracting(s -> s.passings).containsExactly(2L, 1L);
    }
}
