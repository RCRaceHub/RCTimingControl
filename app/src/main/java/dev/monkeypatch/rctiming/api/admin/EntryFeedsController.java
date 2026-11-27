package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.EntryFeedSummaryDto;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every event's entry feed in short, so the Events list can show which events had their entries updated, wait for a
 * decision or are no longer on RaceHub. Under {@code /api/v1/admin/entry-feeds}; ADMIN only, like the feeds.
 */
@RestController
@RequestMapping("/api/v1/admin/entry-feeds")
@AdminOnly
public class EntryFeedsController {

    private final EntryFeedService feedService;

    public EntryFeedsController(EntryFeedService feedService) {
        this.feedService = feedService;
    }

    /** One short row per event that has a feed; addresses and tokens are never included. */
    @GetMapping
    public List<EntryFeedSummaryDto> list() {
        return feedService.findAllFeeds().stream().map(EntryFeedSummaryDto::of).toList();
    }
}
