package dev.monkeypatch.rctiming.domain.entryfeed;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fetches every entry feed with automatic fetch turned on, every few minutes (#42). Runs on the scheduler thread,
 * so race control never waits on the network, and each feed's outcome is recorded on it rather than thrown.
 */
@Component
public class EntryFeedScheduler {

    private final EntryFeedRepository feedRepository;
    private final EntryFeedService feedService;

    public EntryFeedScheduler(EntryFeedRepository feedRepository, EntryFeedService feedService) {
        this.feedRepository = feedRepository;
        this.feedService = feedService;
    }

    @Scheduled(initialDelayString = "${rctiming.entry-feed.interval-ms:300000}",
            fixedDelayString = "${rctiming.entry-feed.interval-ms:300000}")
    /**
     * Fetches each auto-fetch feed in turn; the interval is {@code rctiming.entry-feed.interval-ms}, five minutes by
     * default.
     */
    public void fetchAll() {
        feedRepository.findByAutoFetchTrue().forEach(feedService::autoFetch);
    }
}
