package dev.monkeypatch.rctiming.domain.entryfeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.config.LoopbackHosts;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportService;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pulls an event's entries from a URL (#42). RCTC runs on a venue laptop that booking systems usually can't
 * reach, so it fetches their Entry Export v1 file instead of being sent it, and imports it with
 * {@link RaceHubImportService}.
 *
 * <p>"Fetch now" never imports by itself: it holds the file for an official, who previews and confirms it. The
 * automatic fetch imports a new revision when it applies cleanly, and holds it for an official when something
 * (an unmapped class, an invalid row) would block it. A revision already applied is a no-op.
 *
 * <p>The network call never runs inside a database transaction, and no failure is thrown to the caller: it is
 * recorded on the feed and shown on the event. Recording an outcome reads the feed again and saves it in one
 * transaction ({@link #update}), so it cannot overwrite a change an official made during the fetch.
 */
@Service
public class EntryFeedService {

    private static final Logger log = LoggerFactory.getLogger(EntryFeedService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final EntryFeedRepository feedRepository;
    private final EventRepository eventRepository;
    private final EntryFeedClient client;
    private final RaceHubImportService importService;
    private final TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final AuditService audit;

    public EntryFeedService(EntryFeedRepository feedRepository, EventRepository eventRepository,
                            EntryFeedClient client, RaceHubImportService importService, TokenCipher tokenCipher,
                            ObjectMapper objectMapper, TransactionTemplate transactions,
                            AuditService audit) {
        this.feedRepository = feedRepository;
        this.eventRepository = eventRepository;
        this.client = client;
        this.importService = importService;
        this.tokenCipher = tokenCipher;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.audit = audit;
    }

    public Optional<EntryFeed> find(long eventId) {
        requireEvent(eventId);
        return feedRepository.findByEventId(eventId);
    }

    /**
     * Saves the event's feed settings.
     *
     * @param token the access token: null keeps the saved one, blank removes it
     */
    @Transactional
    public EntryFeed save(Actor actor, long eventId, String url, String token, boolean autoFetch) {
        Event event = requireEvent(eventId);
        String checkedUrl = checkUrl(url).toString();
        Optional<EntryFeed> existing = feedRepository.findByEventId(eventId);
        Map<String, Object> before = existing.map(EntryFeedService::settingsOf).orElse(null);
        EntryFeed feed = existing.orElseGet(() -> {
            EntryFeed created = new EntryFeed();
            created.setEventId(eventId);
            return created;
        });
        if (!checkedUrl.equals(feed.getUrl())) {
            // Nothing fetched from another address belongs to this feed any more
            feed.setHeldDocument(null);
            feed.setHeldRevision(null);
            feed.setAppliedRevision(null);
            feed.setLastFetchAt(null);
            feed.setLastStatus(null);
            feed.setLastError(null);
        }
        feed.setUrl(checkedUrl);
        if (token != null) {
            String trimmed = token.trim();
            feed.setTokenEncrypted(trimmed.isEmpty() ? null : tokenCipher.encrypt(trimmed));
            feed.setTokenHint(trimmed.length() < 8 ? null : trimmed.substring(trimmed.length() - 4));
        }
        feed.setAutoFetch(autoFetch);
        EntryFeed saved = feedRepository.save(feed);
        // The token itself is never recorded, only whether one is saved and whether this save changed it
        Map<String, Object> after = settingsOf(saved);
        after.put("tokenChanged", token != null);
        audit.entry(actor, before == null ? "ENTRY_FEED_CREATED" : "ENTRY_FEED_UPDATED")
                .entity("entry_feed", saved.getId()).event(eventId)
                .summary("Saved the entry feed settings of " + event.getName())
                .before(before).after(after).record();
        return saved;
    }

    @Transactional
    public void delete(Actor actor, long eventId) {
        Event event = requireEvent(eventId);
        Optional<EntryFeed> existing = feedRepository.findByEventId(eventId);
        feedRepository.deleteByEventId(eventId);
        existing.ifPresent(feed -> audit.entry(actor, "ENTRY_FEED_DELETED").entity("entry_feed", feed.getId())
                .event(eventId).summary("Removed the entry feed of " + event.getName())
                .before(settingsOf(feed)).record());
    }

    /** The feed's settings for an audit row: never the token, only whether one is saved. */
    private static Map<String, Object> settingsOf(EntryFeed feed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", feed.getUrl());
        m.put("autoFetch", feed.isAutoFetch());
        m.put("tokenSaved", feed.getTokenEncrypted() != null);
        return m;
    }

    /**
     * "Fetch now": downloads the file and, when it is a new revision, holds it for an official to preview and
     * confirm. Nothing is imported.
     */
    public EntryFeed fetchNow(long eventId) {
        EntryFeed feed = requireFeed(eventId);
        Download download = download(feed);
        if (download.problem != null) {
            return removedWhileFetching(recordFailure(feed, download.problem));
        }
        RaceHubEntryExport export = download.export;
        if (Objects.equals(export.revision(), feed.getAppliedRevision())) {
            return removedWhileFetching(recordUnchanged(feed));
        }
        try {
            // Checks the file can be imported at all (schema version, source) before holding it
            importService.preview(eventId, export);
        } catch (IllegalArgumentException e) {
            return removedWhileFetching(recordFailure(feed, new Problem(EntryFeedStatus.FAILED, e.getMessage())));
        }
        return removedWhileFetching(
                hold(feed, export, "Fetched revision " + export.revision() + ". Check it and confirm the import."));
    }

    /** The import the held file would make, without saving anything. */
    public RaceHubImportResult previewHeld(long eventId) {
        EntryFeed feed = requireFeed(eventId);
        return importService.preview(eventId, heldExport(feed));
    }

    /** Imports the held file. When something blocks it, nothing is saved and the file stays held. */
    public RaceHubImportResult applyHeld(Actor actor, long eventId) {
        EntryFeed feed = requireFeed(eventId);
        RaceHubEntryExport export = heldExport(feed);
        RaceHubImportResult result = importService.importEntries(actor, eventId, export, false);
        if (result.applied()) {
            recordApplied(feed, export.revision());
        }
        return result;
    }

    /**
     * The automatic fetch: imports a new revision when it applies cleanly, and holds it for an official when
     * something would block it. Never throws.
     */
    public void autoFetch(EntryFeed feed) {
        try {
            Download download = download(feed);
            if (download.problem != null) {
                recordFailure(feed, download.problem);
                return;
            }
            RaceHubEntryExport export = download.export;
            if (Objects.equals(export.revision(), feed.getAppliedRevision())) {
                recordUnchanged(feed);
                return;
            }
            RaceHubImportResult result;
            try {
                result = importService.importEntries(Actor.system("entry-feed"), feed.getEventId(), export, false);
            } catch (IllegalArgumentException e) {
                recordFailure(feed, new Problem(EntryFeedStatus.FAILED, e.getMessage()));
                return;
            }
            if (result.applied()) {
                recordApplied(feed, export.revision());
                log.info("Entry feed for event {} applied revision {}", feed.getEventId(), export.revision());
            } else {
                hold(feed, export, "Revision " + export.revision() + " needs an official: " + blockReason(result));
            }
        } catch (RuntimeException e) {
            log.warn("Entry feed for event {} failed", feed.getEventId(), e);
            recordFailure(feed, new Problem(EntryFeedStatus.FAILED,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private record Problem(EntryFeedStatus status, String message) {
    }

    private record Download(RaceHubEntryExport export, Problem problem) {
    }

    private Download download(EntryFeed feed) {
        String token;
        try {
            token = feed.getTokenEncrypted() == null ? null : tokenCipher.decrypt(feed.getTokenEncrypted());
        } catch (TokenCipher.UnreadableTokenException e) {
            return new Download(null, new Problem(EntryFeedStatus.AUTH_FAILED, e.getMessage()));
        }
        EntryFeedClient.Result result = client.fetch(URI.create(feed.getUrl()), token);
        return switch (result) {
            case EntryFeedClient.AuthFailed failed ->
                    new Download(null, new Problem(EntryFeedStatus.AUTH_FAILED, failed.message()));
            case EntryFeedClient.Failed failed -> new Download(null, new Problem(EntryFeedStatus.FAILED, failed.message()));
            case EntryFeedClient.Fetched fetched -> parse(fetched.body());
        };
    }

    private Download parse(String body) {
        RaceHubEntryExport export;
        try {
            export = objectMapper.readValue(body, RaceHubEntryExport.class);
        } catch (JsonProcessingException e) {
            return new Download(null, new Problem(EntryFeedStatus.FAILED, "The feed didn't answer with an entry file"));
        }
        if (export == null || export.revision() == null) {
            return new Download(null, new Problem(EntryFeedStatus.FAILED, "The feed's file has no revision"));
        }
        return new Download(export, null);
    }

    private Optional<EntryFeed> hold(EntryFeed feed, RaceHubEntryExport export, String message) {
        String document;
        try {
            // Only the fields the import reads are kept, so nothing personal the file carried is stored
            document = objectMapper.writeValueAsString(export);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the fetched file", e);
        }
        return update(feed, f -> {
            f.setHeldDocument(document);
            f.setHeldRevision(export.revision());
            f.setLastStatus(EntryFeedStatus.WAITING);
            f.setLastError(message);
        });
    }

    private void recordApplied(EntryFeed feed, Long revision) {
        update(feed, f -> {
            f.setAppliedRevision(revision);
            f.setHeldDocument(null);
            f.setHeldRevision(null);
            f.setLastStatus(EntryFeedStatus.APPLIED);
            f.setLastError(null);
        });
    }

    private Optional<EntryFeed> recordUnchanged(EntryFeed feed) {
        return update(feed, f -> {
            // A file waiting for an official stays waiting; there's just nothing newer
            if (f.getHeldDocument() == null) {
                f.setLastStatus(EntryFeedStatus.UNCHANGED);
                f.setLastError(null);
            }
        });
    }

    private Optional<EntryFeed> recordFailure(EntryFeed feed, Problem problem) {
        String message = problem.message() == null ? "The fetch failed" : problem.message();
        return update(feed, f -> {
            f.setLastStatus(problem.status());
            f.setLastError(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        });
    }

    /**
     * Applies a change to the feed as it is now, since an official may have changed its settings during the
     * fetch. The read and the save are one transaction, so a change made in between is never overwritten.
     * A feed removed meanwhile stays removed (empty result), and one moved to another URL keeps no outcome
     * from the old one (it is returned as it is).
     */
    private Optional<EntryFeed> update(EntryFeed fetched, java.util.function.Consumer<EntryFeed> change) {
        return transactions.execute(status -> {
            Optional<EntryFeed> current = feedRepository.findByEventId(fetched.getEventId());
            if (current.isEmpty()) {
                return Optional.empty();
            }
            EntryFeed feed = current.get();
            if (!Objects.equals(feed.getUrl(), fetched.getUrl())) {
                // The URL changed during the fetch, so the outcome belongs to the old address
                return Optional.of(feed);
            }
            Instant now = Instant.now();
            change.accept(feed);
            feed.setLastFetchAt(now);
            return Optional.of(feedRepository.save(feed));
        });
    }

    /** "Fetch now" answers with the feed's new state; a feed deleted during the fetch is a 404, not a null. */
    private EntryFeed removedWhileFetching(Optional<EntryFeed> recorded) {
        return recorded.orElseThrow(() -> new EntityNotFoundException("The entry feed was removed while it was fetched"));
    }

    private RaceHubEntryExport heldExport(EntryFeed feed) {
        if (feed.getHeldDocument() == null) {
            throw new EntityNotFoundException("No fetched file is waiting for this event");
        }
        try {
            return objectMapper.readValue(feed.getHeldDocument(), RaceHubEntryExport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The held file can't be read", e);
        }
    }

    private static String blockReason(RaceHubImportResult result) {
        if (!result.unmappedClasses().isEmpty()) {
            int count = result.unmappedClasses().size();
            return count + (count == 1 ? " class needs" : " classes need") + " mapping";
        }
        return result.errors().isEmpty() ? "the import is blocked" : result.errors().get(0);
    }

    private Event requireEvent(long eventId) {
        return eventRepository.getOrThrow(eventId);
    }

    private EntryFeed requireFeed(long eventId) {
        requireEvent(eventId);
        return feedRepository.findByEventId(eventId)
                .orElseThrow(() -> new EntityNotFoundException("This event has no entry feed"));
    }

    /** The token goes with every request, so only https is allowed, or plain http to this machine for testing. */
    static URI checkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("The feed URL is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The feed URL isn't a valid address");
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null) {
            throw new IllegalArgumentException("The feed URL needs to be a full address, such as https://…");
        }
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean localHttp = "http".equalsIgnoreCase(scheme) && LoopbackHosts.isLoopback(uri.getHost());
        if (!https && !localHttp) {
            throw new IllegalArgumentException("The feed URL must start with https://, so the token isn't sent "
                    + "in the clear (plain http works only to this computer)");
        }
        return uri;
    }
}
