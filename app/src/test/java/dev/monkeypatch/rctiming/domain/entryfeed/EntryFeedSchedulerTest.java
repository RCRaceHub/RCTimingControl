package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which feeds are due: five minutes within a week of the event, an hour before that, and never-fetched at once. */
class EntryFeedSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneId.systemDefault());

    private final EntryFeedRepository feeds = mock(EntryFeedRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final EntryFeedScheduler scheduler = new EntryFeedScheduler(feeds, mock(EntryFeedService.class), events,
            new EntryFeedProperties(300_000, 3_600_000, 7), Clock.fixed(NOW, ZoneId.of("UTC")));

    private EntryFeed feedFetchedAgo(long minutes, LocalDate eventDate) {
        EntryFeed feed = new EntryFeed();
        feed.setEventId(1L);
        feed.setLastFetchAt(minutes < 0 ? null : NOW.minusSeconds(minutes * 60));
        Event event = mock(Event.class);
        when(event.getEventDate()).thenReturn(eventDate);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        return feed;
    }

    @Test
    void aFeedNeverFetched_isDue() {
        assertThat(scheduler.isDue(feedFetchedAgo(-1, TODAY.plusDays(60)), NOW)).isTrue();
    }

    @Test
    void anEventWithinAWeek_isDueAfterFiveMinutes() {
        assertThat(scheduler.isDue(feedFetchedAgo(4, TODAY.plusDays(3)), NOW)).isFalse();
        assertThat(scheduler.isDue(feedFetchedAgo(5, TODAY.plusDays(3)), NOW)).isTrue();
    }

    @Test
    void anEventFurtherOff_isDueHourly() {
        assertThat(scheduler.isDue(feedFetchedAgo(30, TODAY.plusDays(30)), NOW)).isFalse();
        assertThat(scheduler.isDue(feedFetchedAgo(61, TODAY.plusDays(30)), NOW)).isTrue();
    }

    @Test
    void aLaptopThatWasOffForDays_findsEveryFeedOverdue() {
        assertThat(scheduler.isDue(feedFetchedAgo(60 * 24 * 3, TODAY.plusDays(30)), NOW)).isTrue();
        assertThat(scheduler.isDue(feedFetchedAgo(60 * 24 * 3, TODAY.plusDays(2)), NOW)).isTrue();
    }
}
