package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Fetches the entry feeds with automatic fetch turned on (#42). Runs on the scheduler thread, so race control never
 * waits on the network, and each feed's outcome is recorded on it rather than thrown.
 *
 * <p>The scheduling is by what is <em>due</em>, not by the clock: a feed is due when it has never been fetched or the
 * time since its last fetch has passed its interval ({@link EntryFeedProperties}: five minutes within a week of the
 * event, an hour before that). The laptop is often switched off, so shortly after start-up every overdue feed is
 * fetched, one after another, and one fetch catches up on every revision missed meanwhile. Missed ticks are never
 * replayed.
 */
@Component
@EnableConfigurationProperties(EntryFeedProperties.class)
public class EntryFeedScheduler {

    private final EntryFeedRepository feedRepository;
    private final EntryFeedService feedService;
    private final EventRepository eventRepository;
    private final EntryFeedProperties properties;
    private final Clock clock;

    @Autowired
    public EntryFeedScheduler(EntryFeedRepository feedRepository, EntryFeedService feedService,
                              EventRepository eventRepository, EntryFeedProperties properties) {
        this(feedRepository, feedService, eventRepository, properties, Clock.systemUTC());
    }

    EntryFeedScheduler(EntryFeedRepository feedRepository, EntryFeedService feedService,
                       EventRepository eventRepository, EntryFeedProperties properties, Clock clock) {
        this.feedRepository = feedRepository;
        this.feedService = feedService;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Fetches every auto-fetch feed whose turn has come. Runs a minute after the last run ended, 30 seconds after start-up. */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void fetchDue() {
        Instant now = clock.instant();
        feedRepository.findByAutoFetchTrue().stream()
                .filter(feed -> isDue(feed, now))
                .forEach(feedService::autoFetch);
    }

    /** Fetches every auto-fetch feed now, due or not (completed events are skipped by the service). */
    public void fetchAll() {
        feedRepository.findByAutoFetchTrue().forEach(feedService::autoFetch);
    }

    /** Whether {@code feed} has waited out its interval; a feed never fetched, or with an unknown event date, is due. */
    boolean isDue(EntryFeed feed, Instant now) {
        // A completed event is never fetched (and its fetch time never moves), so it must not be due every minute
        if (eventRepository.findById(feed.getEventId()).filter(e -> e.getStatus() == EventStatus.COMPLETED).isPresent()) {
            return false;
        }
        Instant last = feed.getLastFetchAt();
        if (last == null) {
            return true;
        }
        return Duration.between(last, now).toMillis() >= intervalMs(feed, now);
    }

    private long intervalMs(EntryFeed feed, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        return eventRepository.findById(feed.getEventId())
                .map(event -> event.getEventDate() != null
                        && event.getEventDate().isAfter(today.plusDays(properties.nearDays()))
                        ? properties.farIntervalMs() : properties.intervalMs())
                .orElse(properties.intervalMs());
    }
}
