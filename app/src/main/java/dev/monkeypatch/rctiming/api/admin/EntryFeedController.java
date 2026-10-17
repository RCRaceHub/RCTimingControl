package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeed;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedStatus;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * An event's entry feed: where its entries are pulled from, fetching, and confirming what was fetched (#42). Under
 * {@code /api/v1/admin/events/{eventId}/entry-feed}; ADMIN only. An unknown event is a 404.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/entry-feed")
@PreAuthorize("hasRole('ADMIN')")
public class EntryFeedController {

    private final EntryFeedService feedService;

    public EntryFeedController(EntryFeedService feedService) {
        this.feedService = feedService;
    }

    /** The feed's settings and latest fetch, or 204 when the event has none. */
    @GetMapping
    public ResponseEntity<EntryFeedDto> get(@PathVariable long eventId) {
        return feedService.find(eventId).map(EntryFeedDto::of).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Saves the settings. A null token keeps the saved one; an empty one removes it. {@code PUT}. Audited.
     * The token is stored encrypted and never sent back.
     */
    @Audited("audit_log")
    @PutMapping
    public EntryFeedDto save(Authentication auth, @PathVariable long eventId, @RequestBody SaveRequest request) {
        return EntryFeedDto.of(feedService.save(CurrentOfficial.actor(auth), eventId, request.url(), request.token(),
                Boolean.TRUE.equals(request.autoFetch())));
    }

    /** Removes the event's feed settings. {@code DELETE}; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable long eventId) {
        feedService.delete(CurrentOfficial.actor(auth), eventId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Fetches the file now and holds a new revision for an official to confirm. Failures are in the result.
     * {@code POST /fetch}. Nothing is imported until {@code /apply}.
     */
    @PostMapping("/fetch")
    public EntryFeedDto fetch(@PathVariable long eventId) {
        return EntryFeedDto.of(feedService.fetchNow(eventId));
    }

    /** What importing the held file would do. Saves nothing. {@code POST /preview}. */
    @PostMapping("/preview")
    public RaceHubImportResult preview(@PathVariable long eventId) {
        return feedService.previewHeld(eventId);
    }

    /**
     * Imports the held file, or answers 422 with the preview when something blocks it. {@code POST /apply}.
     * Audited.
     */
    @Audited("audit_log")
    @PostMapping("/apply")
    public ResponseEntity<RaceHubImportResult> apply(Authentication auth, @PathVariable long eventId) {
        RaceHubImportResult result = feedService.applyHeld(CurrentOfficial.actor(auth), eventId);
        return ImportResponses.of(result);
    }

    /**
     * The body of {@code PUT}: the address of the Entry Export file, its access token (see {@code save}) and whether
     * the app fetches it by itself (null counts as off).
     */
    public record SaveRequest(String url, String token, Boolean autoFetch) {
    }

    /**
     * The feed as the page shows it. The token itself is never sent back: only whether one is saved and its last
     * four characters. {@code lastFetchAt}, {@code lastStatus} and {@code lastMessage} describe the latest fetch;
     * {@code appliedRevision} is the revision last imported; {@code waiting} says a fetched file is held for an
     * official to confirm, and {@code waitingRevision} is its revision.
     */
    public record EntryFeedDto(String url, boolean tokenSaved, String tokenHint, boolean autoFetch,
                               Instant lastFetchAt, EntryFeedStatus lastStatus, String lastMessage,
                               Long appliedRevision, boolean waiting, Long waitingRevision) {
        static EntryFeedDto of(EntryFeed f) {
            return new EntryFeedDto(f.getUrl(), f.getTokenEncrypted() != null, f.getTokenHint(), f.isAutoFetch(),
                    f.getLastFetchAt(), f.getLastStatus(), f.getLastError(), f.getAppliedRevision(),
                    f.getHeldDocument() != null, f.getHeldRevision());
        }
    }
}
