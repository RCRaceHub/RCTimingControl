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

class DumpComparisonTest {

    private static String line(long wallMs, int seq, String transponder) {
        return wallMs + "\t@\t20\t" + seq + "\t" + transponder + "\t" + seq + ".0\t400\t163\t2\txB\n";
    }

    private static DumpSummary summary(Path dir, String name, String times) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, "");
        Files.writeString(dir.resolve(name + ".times"), times, StandardCharsets.ISO_8859_1);
        return DumpSummary.read(file);
    }

    @Test
    void pairsTheSameCarAtBothDecodersAndMeasuresTheMedianArrivalDifference(@TempDir Path dir) throws IOException {
        DumpSummary a = summary(dir, "a.dump", line(1_000, 1, "100") + line(11_000, 2, "100") + line(21_000, 3, "100"));
        DumpSummary b = summary(dir, "b.dump", line(1_030, 1, "100") + line(11_050, 2, "100") + line(21_040, 3, "100"));

        DumpComparison.Result r = DumpComparison.compare("a", a, "b", b, 2_000);

        assertThat(r.paired()).isEqualTo(3);
        assertThat(r.unpairedA()).isZero();
        assertThat(r.unpairedB()).isZero();
        assertThat(r.medianMs()).isEqualTo(40);
        assertThat(r.minMs()).isEqualTo(30);
        assertThat(r.maxMs()).isEqualTo(50);
    }

    @Test
    void leavesAPassingOneDecoderMissedAndACarOnlyOneSawUnpaired(@TempDir Path dir) throws IOException {
        DumpSummary a = summary(dir, "a.dump", line(1_000, 1, "100") + line(11_000, 2, "100") + line(12_000, 3, "200"));
        DumpSummary b = summary(dir, "b.dump", line(1_020, 1, "100") + line(30_000, 2, "300"));

        DumpComparison.Result r = DumpComparison.compare("a", a, "b", b, 2_000);

        assertThat(r.paired()).isEqualTo(1);
        assertThat(r.unpairedA()).isEqualTo(2);
        assertThat(r.unpairedB()).isEqualTo(1);
    }

    @Test
    void doesNotPairArrivalsFurtherApartThanTheWindow(@TempDir Path dir) throws IOException {
        DumpSummary a = summary(dir, "a.dump", line(1_000, 1, "100"));
        DumpSummary b = summary(dir, "b.dump", line(5_000, 1, "100"));

        assertThat(DumpComparison.compare("a", a, "b", b, 2_000).paired()).isZero();
        assertThat(DumpComparison.compare("a", a, "b", b, 5_000).paired()).isEqualTo(1);
    }

    @Test
    void printsTheOffsetToSetOnTheSecondDecoder(@TempDir Path dir) throws IOException {
        DumpSummary a = summary(dir, "a.dump", line(1_000, 1, "100"));
        DumpSummary b = summary(dir, "b.dump", line(1_025, 1, "100"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DumpComparison.print(new PrintStream(out, true, StandardCharsets.UTF_8), "a", a, "b", b, 2_000);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("Paired passings: 1")
                .contains("median 25 ms")
                .contains("clock offset to -25 ms");
    }

    @Test
    void saysItNeedsTheTimesFileWhenARecordingHasNone(@TempDir Path dir) throws IOException {
        DumpSummary a = summary(dir, "a.dump", line(1_000, 1, "100"));
        Path bare = dir.resolve("b.dump");
        Files.writeString(bare, "\u0001@\t20\t1\t100\t1.0\t400\t163\t2\txB\r\n", StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DumpComparison.print(new PrintStream(out, true, StandardCharsets.UTF_8), "a", a, "b", DumpSummary.read(bare), 2_000);

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("need their .times file");
    }
}
