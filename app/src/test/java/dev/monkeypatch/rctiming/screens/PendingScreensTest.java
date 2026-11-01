package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerInfo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PendingScreensTest {

    private static ViewerInfo info(String id, String name) {
        return new ViewerInfo(id, name, null, "1.0", "1", null, false, null);
    }

    @Test
    void aScreenWithNoIdIsIgnored() {
        PendingScreens pending = new PendingScreens(Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));

        pending.put(info(" ", "Nameless"), "10.0.0.5", 8080);

        assertThat(pending.list()).isEmpty();
    }

    @Test
    void aLongNameIsClipped() {
        PendingScreens pending = new PendingScreens(Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));

        pending.put(info("s1", "x".repeat(1000)), "10.0.0.5", 8080);

        assertThat(pending.list()).singleElement().satisfies(f -> assertThat(f.name()).hasSize(255));
    }

    @Test
    void aScreenNotHeardForFiveMinutesDropsOut() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        PendingScreens pending = new PendingScreens(clock);
        pending.put(info("s1", "Pit"), "10.0.0.5", 8080);

        clock.advance(Duration.ofMinutes(4));
        assertThat(pending.list()).hasSize(1);
        clock.advance(Duration.ofMinutes(2));
        assertThat(pending.list()).isEmpty();
    }

    @Test
    void noMoreThanTheCapAreKept() {
        PendingScreens pending = new PendingScreens(Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));

        for (int i = 0; i < PendingScreens.MAX + 20; i++) {
            pending.put(info("s" + i, "Screen " + i), "10.0.0." + (i % 250), 8080);
        }

        assertThat(pending.list()).hasSize(PendingScreens.MAX);
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
