package dev.monkeypatch.rctiming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the test conventions in {@code docs/testing.md}: a test that has to wait for something uses Awaitility
 * or a latch, not {@code Thread.sleep}, which is slow when the wait is long and flaky when it is short (#620).
 * A few sleeps are not waits for the code under test but part of a simulated slow peer, a thread holding a
 * transaction, or a clock tick that must pass; they are listed here, each with its reason.
 */
class TestConventionsTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path PACKAGE = Path.of("dev/monkeypatch/rctiming");

    /** Files, relative to {@code dev/monkeypatch/rctiming/}, whose {@code Thread.sleep} is deliberate, with why. */
    private static final Set<String> SLEEP_ALLOWED = Set.of(
            // A fake decoder that stays silent or trickles bytes, to test the probe's timeouts.
            "timing/DecoderProbeTest.java",
            // The access token's issued-at is to the second, so the test lets one pass.
            "api/admin/OfficialControllerIT.java",
            // A second writer holds its transaction open while the test works on the first.
            "domain/race/RaceAndMarshallingRepositoriesIT.java",
            // Paces real network reconnect attempts against the test clock.
            "livefeed/LiveFeedIT.java",
            // Two saves must get different update times.
            "persistence/TimestampsIT.java",
            // This file names the call in its own rule.
            "TestConventionsTest.java");

    @Test
    void testsWaitWithAwaitilityNotThreadSleep() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            violations = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> SLEEP_ALLOWED.stream().noneMatch(allowed -> p.endsWith(PACKAGE.resolve(allowed))))
                    .filter(TestConventionsTest::sleeps)
                    .map(p -> TEST_SOURCES.relativize(p).toString())
                    .toList();
        }
        assertThat(violations)
                .as("tests that call Thread.sleep: wait with Awaitility, or add the file to SLEEP_ALLOWED with a reason")
                .isEmpty();
    }

    private static boolean sleeps(Path file) {
        try {
            return Files.readString(file).contains("Thread." + "sleep(");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
